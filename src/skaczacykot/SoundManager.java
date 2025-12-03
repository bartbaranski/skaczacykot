package skaczacykot;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class SoundManager {
    private Clip bgLoop;
    private final List<Clip> sfx = new ArrayList<>();

    void playBgLoop(String wavPath) {
        stopBg();
        bgLoop = loadClipFromResource("/resources/audio/" + wavPath);
        if (bgLoop != null) {
            bgLoop.loop(Clip.LOOP_CONTINUOUSLY);
            bgLoop.start();
        }
    }

    void stopBg() {
        if (bgLoop != null) {
            bgLoop.stop();
            bgLoop.close();
            bgLoop = null;
        }
    }

    void playSfx(String wavPath) {
        Clip c = loadClipFromResource("/resources/audio/" + wavPath);
        if (c != null) {
            sfx.add(c);
            c.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    c.close();
                    sfx.remove(c);
                }
            });
            c.start();
        }
    }

    void stopAll() {
        stopBg();
        for (Clip c : new ArrayList<>(sfx)) {
            c.stop();
            c.close();
        }
        sfx.clear();
    }

    private Clip loadClipFromResource(String fullPath) {
        try (InputStream is = SoundManager.class.getResourceAsStream(fullPath)) {
            if (is == null) return null;
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new javax.sound.sampled.AudioInputStream(is, AudioSystem.getAudioInputStream(SoundManager.class.getResource(fullPath)).getFormat(), AudioSystem.getAudioInputStream(SoundManager.class.getResource(fullPath)).getFrameLength()))) {
                Clip clip = AudioSystem.getClip();
                // prostsza, bezpotrzebnie złożona linia powyżej bywa wrażliwa w niektórych JDK; użyj tego:
            }
        } catch (Exception ignore) {}
        // Fallback – prostsze otwarcie (najczęściej działa)
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(SoundManager.class.getResource(fullPath));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Exception e) {
            return null;
        }
    }
}
