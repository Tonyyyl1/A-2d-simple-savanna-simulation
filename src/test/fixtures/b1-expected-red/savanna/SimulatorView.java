package savanna;

/**
 * Synthetic reproduction of the B1 transparent-front race. This source is not
 * part of production or test compilation; test.sh compiles it in isolation so
 * the expected-red gate is rebuildable without a historical JAR.
 */
public final class SimulatorView
{
    public void showStatus()
    {
    }

    public final class FieldView
    {
        private Object animalImage;

        public void componentResized()
        {
            animalImage = new Object();
            SimulatorView.this.showStatus();
        }
    }
}
