package savanna;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rejects incomplete, mixed, duplicated, or tampered production runtimes. */
public final class RuntimeClasspathGuard
{
    public static final String RUNTIME_TREE_RESOURCE =
        "META-INF/savanna-runtime-tree.sha256";

    private RuntimeClasspathGuard()
    {
    }

    public static void verifyOrThrow()
    {
        RuntimeLaunchIdentity.currentCandidateClass();
        BuildIdentity identity = BuildIdentity.current();
        if(!identity.isStamped()) {
            throw new IllegalStateException(
                "UNSTAMPED runtime refused. Use ./build.sh and a canonical " +
                "launcher; unstamped runs cannot produce trusted evidence.");
        }

        List<String> problems = new ArrayList<>();
        int stamps = resourceCount(BuildIdentity.RESOURCE);
        if(stamps != 1) {
            problems.add(BuildIdentity.RESOURCE + " has " + stamps +
                         " visible classpath copies");
        }
        int manifests = resourceCount(RUNTIME_TREE_RESOURCE);
        if(manifests != 1) {
            problems.add("runtime-tree identity manifest " +
                         RUNTIME_TREE_RESOURCE + " has " + manifests +
                         " visible classpath copies");
        }
        if(!problems.isEmpty()) {
            throw failure(problems);
        }

        byte[] manifestBytes = resourceBytes(RUNTIME_TREE_RESOURCE);
        String manifestHash = sha256(manifestBytes);
        if(!manifestHash.equals(identity.getRuntimeTreeHash())) {
            problems.add("runtime-tree identity manifest hash " + manifestHash +
                         " differs from build stamp " +
                         identity.getRuntimeTreeHash());
        }
        Map<String, String> expectedEntries = parseManifest(manifestBytes);
        if(!expectedEntries.containsKey(
                "savanna/RuntimeClasspathGuard.class") ||
           !expectedEntries.containsKey("savanna/BuildIdentity.class") ||
           !expectedEntries.containsKey(
                "savanna/VisualSimulationRunner.class") ||
           !expectedEntries.containsKey("savanna/SimulationRunner.class")) {
            problems.add("runtime-tree identity manifest omits a required " +
                         "entry-point identity class");
        }

        URI expectedCodeSource =
            BuildIdentity.locateCodeSource(RuntimeClasspathGuard.class);
        ClassLoader loader = RuntimeClasspathGuard.class.getClassLoader();
        int classCount = 0;
        for(Map.Entry<String, String> entry : expectedEntries.entrySet()) {
            String resource = entry.getKey();
            int copies = resourceCount(resource);
            if(copies != 1) {
                problems.add(resource + " has " + copies +
                             " visible classpath copies");
                continue;
            }
            String actualHash = sha256(resourceBytes(resource));
            if(!entry.getValue().equals(actualHash)) {
                problems.add(resource + " hash " + actualHash +
                             " differs from runtime manifest " +
                             entry.getValue());
            }
            if(resource.endsWith(".class")) {
                classCount++;
                String className = resource.substring(0,
                    resource.length() - ".class".length()).replace('/', '.');
                try {
                    Class<?> type = Class.forName(className, false, loader);
                    URI actualCodeSource = BuildIdentity.locateCodeSource(type);
                    if(!expectedCodeSource.equals(actualCodeSource)) {
                        problems.add(className + " came from " +
                            actualCodeSource + " instead of " +
                            expectedCodeSource);
                    }
                }
                catch(ClassNotFoundException | LinkageError exception) {
                    problems.add(className + " cannot be loaded from the " +
                                 "declared runtime tree: " + exception);
                }
            }
        }
        if(classCount == 0) {
            problems.add("runtime-tree identity manifest contains no classes");
        }
        if(!problems.isEmpty()) {
            throw failure(problems);
        }
    }

    private static IllegalStateException failure(List<String> problems)
    {
        return new IllegalStateException(
            "Runtime classpath identity check failed: " +
            String.join("; ", problems));
    }

    private static Map<String, String> parseManifest(byte[] bytes)
    {
        Map<String, String> entries = new LinkedHashMap<>();
        String previous = null;
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) {
                if(!line.matches("[0-9a-f]{64}  [^\\s/][^\\s]*")) {
                    throw new IllegalStateException(
                        "Malformed runtime-tree identity manifest row: " + line);
                }
                String hash = line.substring(0, 64);
                String resource = line.substring(66);
                if(resource.contains("\\") || resource.contains("..") ||
                   resource.equals(BuildIdentity.RESOURCE) ||
                   resource.equals(RUNTIME_TREE_RESOURCE) ||
                   resource.equals("META-INF/MANIFEST.MF")) {
                    throw new IllegalStateException(
                        "Forbidden runtime-tree identity manifest path: " +
                        resource);
                }
                if(previous != null && previous.compareTo(resource) >= 0) {
                    throw new IllegalStateException(
                        "Runtime-tree identity manifest paths are not unique " +
                        "and strictly sorted: " + resource);
                }
                entries.put(resource, hash);
                previous = resource;
            }
        }
        catch(IOException exception) {
            throw new IllegalStateException(
                "Could not parse runtime-tree identity manifest", exception);
        }
        if(entries.isEmpty()) {
            throw new IllegalStateException(
                "Runtime-tree identity manifest is empty");
        }
        return entries;
    }

    private static byte[] resourceBytes(String resource)
    {
        ClassLoader loader = RuntimeClasspathGuard.class.getClassLoader();
        try(InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource)) {
            if(input == null) {
                throw new IllegalStateException(
                    "Runtime classpath resource is missing: " + resource);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
        catch(IOException exception) {
            throw new IllegalStateException(
                "Could not read runtime classpath resource " + resource,
                exception);
        }
    }

    private static String sha256(byte[] bytes)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexEncoding.lowercase(digest);
        }
        catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static int resourceCount(String resource)
    {
        try {
            ClassLoader loader = RuntimeClasspathGuard.class.getClassLoader();
            Enumeration<URL> resources = loader == null
                ? ClassLoader.getSystemResources(resource)
                : loader.getResources(resource);
            int count = 0;
            while(resources.hasMoreElements()) {
                resources.nextElement();
                count++;
            }
            return count;
        }
        catch(IOException exception) {
            throw new IllegalStateException(
                "Could not inspect classpath resource " + resource, exception);
        }
    }
}
