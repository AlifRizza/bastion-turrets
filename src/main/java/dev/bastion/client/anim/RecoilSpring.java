package dev.bastion.client.anim;

/**
 * Damped spring for procedural recoil (PLAN 6.4): each shot adds an impulse, the value springs back with
 * damping. Layered on the keyframed fire animation so rapid fire stays smooth instead of restarting a clip.
 * Integrated per frame with real elapsed time, stable at any frame rate.
 */
public final class RecoilSpring {
    private final float stiffness, damping;
    private float value, velocity;
    private long lastNanos = System.nanoTime();

    /** @param stiffness pull back toward 0 (per s^2); @param damping velocity loss (per s) */
    public RecoilSpring(float stiffness, float damping) {
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void impulse(float amount) {
        velocity += amount;
    }

    /** Current offset; advances the simulation to now in small fixed steps. */
    public float sample() {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanos) / 1e9f, 0.1f);
        lastNanos = now;
        for (float t = 0; t < dt; t += 0.004f) {
            float step = Math.min(0.004f, dt - t);
            velocity += (-stiffness * value - damping * velocity) * step;
            value += velocity * step;
        }
        return value;
    }
}
