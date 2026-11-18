package savanna;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * AST-based A1b dependency and call-edge contract. Source comments and string
 * literals are never treated as dependencies.
 */
public final class A1bDependencyContract
{
    private static final Set<String> KERNEL_FILES = Set.of(
        "ResourceVector.java",
        "BirthResourceCommandType.java",
        "BirthResourceLedgerEntry.java",
        "BirthResourceTransaction.java",
        "BirthResourceTransactionCommand.java",
        "BirthResourceTransactionPhase.java",
        "BirthResourceTransactionPlan.java",
        "BirthResourceTransactionResult.java",
        "BirthResourceTransactionState.java");

    private static final Set<String> KERNEL_TYPES = Set.of(
        "ResourceVector",
        "BirthResourceCommandType",
        "BirthResourceLedgerEntry",
        "BirthResourceTransaction",
        "BirthResourceTransactionCommand",
        "BirthResourceTransactionPhase",
        "BirthResourceTransactionPlan",
        "BirthResourceTransactionResult",
        "BirthResourceTransactionState");

    private A1bDependencyContract() {}

    public static final class Scan
    {
        private final Set<String> transitionOwners;
        private final Set<String> kernelConsumers;
        private final Map<String, Set<String>> forbiddenByKernelType;
        private final List<String> diagnostics;

        private Scan(Set<String> transitionOwners,
                     Set<String> kernelConsumers,
                     Map<String, Set<String>> forbiddenByKernelType,
                     List<String> diagnostics)
        {
            this.transitionOwners = immutableSet(transitionOwners);
            this.kernelConsumers = immutableSet(kernelConsumers);
            Map<String, Set<String>> forbidden = new LinkedHashMap<>();
            for(Map.Entry<String, Set<String>> entry :
                forbiddenByKernelType.entrySet()) {
                forbidden.put(entry.getKey(), immutableSet(entry.getValue()));
            }
            this.forbiddenByKernelType = Collections.unmodifiableMap(forbidden);
            this.diagnostics = Collections.unmodifiableList(
                new ArrayList<>(diagnostics));
        }

        public Set<String> getTransitionOwners() { return transitionOwners; }
        public Set<String> getKernelConsumers() { return kernelConsumers; }
        public Map<String, Set<String>> getForbiddenByKernelType()
        {
            return forbiddenByKernelType;
        }
        public List<String> getDiagnostics() { return diagnostics; }
    }

    public static final class Verification
    {
        private final Scan scan;
        private final List<String> violations;

        private Verification(Scan scan, List<String> violations)
        {
            this.scan = scan;
            this.violations = Collections.unmodifiableList(
                new ArrayList<>(violations));
        }

        public Scan getScan() { return scan; }
        public boolean isPassed() { return violations.isEmpty(); }
        public List<String> getViolations() { return violations; }
    }

    public static Verification verify(Path sourceRoot,
                                      V24GateProfile profile)
        throws IOException
    {
        if(profile == null) {
            throw new IllegalArgumentException("A1b gate profile is required");
        }
        Scan scan = scan(sourceRoot, profile.getForbiddenDependencies());
        List<String> violations = new ArrayList<>();
        if(!scan.getDiagnostics().isEmpty()) {
            violations.add("ast-diagnostics:" + scan.getDiagnostics());
        }
        String contract = profile.get("a1b.contract");
        if("DISCONNECTED_ZERO_CALLERS".equals(contract)) {
            if(!scan.getTransitionOwners().isEmpty()) {
                violations.add("expected-zero-transition-owners:" +
                    scan.getTransitionOwners());
            }
            if(!scan.getKernelConsumers().isEmpty()) {
                violations.add("expected-zero-kernel-consumers:" +
                    scan.getKernelConsumers());
            }
        }
        else if("CONNECTED_EXACT_ALLOWLIST".equals(contract)) {
            Set<String> expectedOwners = Set.of(
                profile.get("a1b.transition.owner"));
            if(!scan.getTransitionOwners().equals(expectedOwners)) {
                violations.add("transition-owner-set:" +
                    scan.getTransitionOwners());
            }
            if(!scan.getKernelConsumers().equals(
                   profile.getKernelConsumers())) {
                violations.add("kernel-consumer-set:" +
                    scan.getKernelConsumers());
            }
        }
        else {
            violations.add("unknown-a1b-contract:" + contract);
        }
        if(!scan.getForbiddenByKernelType().isEmpty()) {
            violations.add("kernel-forbidden-dependencies:" +
                scan.getForbiddenByKernelType());
        }
        return new Verification(scan, violations);
    }

    public static Scan scan(Path sourceRoot, Set<String> forbiddenDependencies)
        throws IOException
    {
        if(sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException(
                "A1b source root must be a directory");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null) {
            throw new IllegalStateException(
                "A1b AST gate requires a full JDK compiler");
        }
        List<Path> sources = new ArrayList<>();
        try(Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) &&
                path.getFileName().toString().endsWith(".java"))
                .sorted().forEach(sources::add);
        }
        if(sources.isEmpty()) {
            throw new IllegalStateException("A1b source root has no Java files");
        }

        Set<String> owners = new LinkedHashSet<>();
        Set<String> consumers = new LinkedHashSet<>();
        Map<String, Set<String>> forbidden = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> collector =
            new DiagnosticCollector<>();
        try(StandardJavaFileManager manager =
            compiler.getStandardFileManager(collector, null, null)) {
            Iterable<? extends JavaFileObject> units =
                manager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask)compiler.getTask(null, manager,
                collector, List.of("-proc:none"), null, units);
            for(CompilationUnitTree unit : task.parse()) {
                Path file = Path.of(unit.getSourceFile().toUri());
                String fileName = file.getFileName().toString();
                String owner = fileName.substring(0,
                    fileName.length() - ".java".length());
                boolean kernelFile = KERNEL_FILES.contains(fileName);
                DependencyScanner scanner = new DependencyScanner(owner,
                    kernelFile, forbiddenDependencies, owners, consumers,
                    forbidden);
                scanner.scan(unit, null);
            }
        }
        collector.getDiagnostics().forEach(diagnostic -> {
            switch(diagnostic.getKind()) {
                case ERROR:
                    diagnostics.add(diagnostic.getCode() + "@" +
                        diagnostic.getLineNumber());
                    break;
                default:
                    break;
            }
        });
        return new Scan(owners, consumers, forbidden, diagnostics);
    }

    private static final class DependencyScanner extends TreeScanner<Void, Void>
    {
        private final String owner;
        private final boolean kernelFile;
        private final Set<String> forbiddenDependencies;
        private final Set<String> transitionOwners;
        private final Set<String> consumers;
        private final Map<String, Set<String>> forbidden;

        private DependencyScanner(String owner, boolean kernelFile,
                                  Set<String> forbiddenDependencies,
                                  Set<String> transitionOwners,
                                  Set<String> consumers,
                                  Map<String, Set<String>> forbidden)
        {
            this.owner = owner;
            this.kernelFile = kernelFile;
            this.forbiddenDependencies = forbiddenDependencies;
            this.transitionOwners = transitionOwners;
            this.consumers = consumers;
            this.forbidden = forbidden;
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused)
        {
            recordTypeName(tree.getName().toString());
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused)
        {
            recordTypeName(tree.getIdentifier().toString());
            return super.visitMemberSelect(tree, unused);
        }

        @Override
        public Void visitImport(ImportTree tree, Void unused)
        {
            String imported = tree.getQualifiedIdentifier().toString();
            for(String kernelType : KERNEL_TYPES) {
                if(imported.endsWith('.' + kernelType) ||
                   imported.endsWith('.' + kernelType + ".transition")) {
                    if(!kernelFile) consumers.add(owner);
                }
            }
            if(imported.endsWith("BirthResourceTransaction.transition") &&
               tree.isStatic()) {
                transitionOwners.add(owner);
            }
            return super.visitImport(tree, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree,
                                          Void unused)
        {
            if(tree.getMethodSelect() instanceof MemberSelectTree) {
                MemberSelectTree select =
                    (MemberSelectTree)tree.getMethodSelect();
                if("transition".contentEquals(select.getIdentifier()) &&
                   select.getExpression().toString()
                       .endsWith("BirthResourceTransaction")) {
                    if(!kernelFile) transitionOwners.add(owner);
                }
            }
            return super.visitMethodInvocation(tree, unused);
        }

        private void recordTypeName(String name)
        {
            if(KERNEL_TYPES.contains(name) && !kernelFile) consumers.add(owner);
            if(kernelFile && forbiddenDependencies.contains(name)) {
                forbidden.computeIfAbsent(owner,
                    ignored -> new LinkedHashSet<>()).add(name);
            }
        }
    }

    private static Set<String> immutableSet(Set<String> source)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
