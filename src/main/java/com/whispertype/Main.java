package com.whispertype;

import com.whispertype.audio.AudioRecorder;
import com.whispertype.config.AppConfig;
import com.whispertype.dependency.DependencyChecker;
import com.whispertype.hotkey.GlobalHotkeyListener;
import com.whispertype.hotkey.KeyCodeMapper;
import com.whispertype.input.TextInputEngine;
import com.whispertype.recognizer.WhisperRecognizer;
import com.whispertype.tray.TrayManager;
import com.whispertype.ui.SettingsWindow;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private AppConfig config;
    private WhisperRecognizer recognizer;
    private AudioRecorder recorder;
    private TextInputEngine inputEngine;
    private TrayManager tray;
    private GlobalHotkeyListener hotkey;
    private SettingsWindow settingsWindow;
    private File baseDir;
    private String resolvedWhisperCliPath;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WhisperType-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final int HISTORY_LIMIT = 50;
    private final Deque<String> history = new ArrayDeque<>();

    private volatile long recordStartMs = 0;

    public static void main(String[] args) {
        // OpenGL 加速必须在 AWT/Swing 初始化前设置，否则无效且可能导致渲染异常
        System.setProperty("sun.java2d.opengl", "true");

        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        System.setProperty("user.language", "zh");
        System.setProperty("user.country", "CN");
        System.setProperty("user.variant", "");

        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        System.setProperty("stdout.encoding", "UTF-8");
        System.setProperty("stderr.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");

        try {
            File logDir;
            File javaHome = new File(System.getProperty("java.home"));
            if ("runtime".equals(javaHome.getName()) && javaHome.getParentFile() != null) {
                logDir = javaHome.getParentFile();
            } else {
                logDir = new File(System.getProperty("user.home")
                        + File.separator + ".whispertype");
            }
            if (!logDir.exists()) logDir.mkdirs();
            File logFile = new File(logDir, "whispertype.log");
            PrintStream logPs = new PrintStream(new FileOutputStream(logFile, true), true, StandardCharsets.UTF_8.name());
            System.setOut(logPs);
            System.setErr(logPs);
            System.out.println("========== WhisperType 启动 " + new java.util.Date() + " ==========");
        } catch (Exception e) {
            try {
                System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
                System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException ex) {
            }
        }

        try {
            // FlatLaf 轻量扁平外观，作为液态玻璃 UI 的基座
            com.formdev.flatlaf.FlatLightLaf.setup();
            javax.swing.UIManager.put("Component.arc", 12);
            javax.swing.UIManager.put("Button.arc", 12);
            javax.swing.UIManager.put("TextComponent.arc", 10);
            javax.swing.UIManager.put("Component.focusWidth", 0);
        } catch (Exception e) {
        }

        try {
            Toolkit.getDefaultToolkit();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception e) {
            System.err.println("[WARN] AWT Toolkit 预初始化失败: " + e.getMessage());
        }

        new Main().run();
    }

    private void run() {
        config = AppConfig.load();

        baseDir = getBaseDir();
        System.out.println("程序基目录: " + baseDir.getAbsolutePath());

        String modelPath = resolvePath(config.getModelPath(), baseDir);
        String whisperCliPath = resolvePath(config.getWhisperCliPath(), baseDir);

        DependencyChecker depChecker = new DependencyChecker(modelPath, whisperCliPath, baseDir);
        DependencyChecker.Result depResult = depChecker.checkWithAutoDownload();
        if (!depResult.allOk()) {
            JOptionPane.showMessageDialog(null,
                    "依赖检查失败，无法启动：\n" + (depResult.errorMessage == null ? "未知错误" : depResult.errorMessage),
                    "Whisper 语音输入 - 启动失败",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        boolean configChanged = false;
        if (depResult.modelPath != null
                && !depResult.modelPath.equals(modelPath)) {
            config.setModelPath(depResult.modelPath);
            configChanged = true;
        } else {
            modelPath = depResult.modelPath;
        }
        if (depResult.whisperCliPath != null
                && !depResult.whisperCliPath.equals(whisperCliPath)) {
            config.setWhisperCliPath(depResult.whisperCliPath);
            configChanged = true;
        } else {
            whisperCliPath = depResult.whisperCliPath;
        }
        if (configChanged) {
            config.save();
        }
        resolvedWhisperCliPath = whisperCliPath;

        try {
            recognizer = new WhisperRecognizer(
                    whisperCliPath,
                    modelPath,
                    config.getSampleRate());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "加载 Whisper 模型失败：\n" + e.getMessage()
                            + "\n\n请确认 whisper-cli.exe 和 ggml-small.bin 存在于正确路径。",
                    "Whisper 语音输入 - 启动失败",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        recorder = new AudioRecorder(config.getSampleRate(), config.getSampleSizeBits(),
                config.getChannels(), config.getAudioInputDevice());
        inputEngine = new TextInputEngine(config);

        checkAudioInputAvailable();

        tray = new TrayManager();
        tray.setCallback(new TrayManager.TrayCallback() {
            @Override public void onToggleEnabled() { toggleEnabled(); }
            @Override public void onOpenSettings() { openSettings(); }
            @Override public void onSwitchModel() { switchModel(); }
            @Override public void onViewHistory() { showHistory(); }
            @Override public void onExit() { exit(); }
        });
        try {
            tray.init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "初始化系统托盘失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        tray.setEnabled(config.isEnabled());

        hotkey = new GlobalHotkeyListener(
                config.getHotkeyCode(), config.getHotkeyModifiers(), config.isLongPressMode());
        hotkey.setCallback(new GlobalHotkeyListener.HotkeyCallback() {
            @Override public void onPressed() { startRecording(); }
            @Override public void onReleased() { stopAndRecognize(); }
            @Override public void onClickToggle() { handleClickToggle(); }
        });
        try {
            hotkey.start();
        } catch (Exception e) {
            tray.showMessage("Whisper 语音输入", "全局热键注册失败: " + e.getMessage(),
                    java.awt.TrayIcon.MessageType.ERROR);
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JFrame keepAlive = new javax.swing.JFrame("WhisperType-KeepAlive");
            keepAlive.setUndecorated(true);
            keepAlive.setSize(0, 0);
            keepAlive.setLocation(-10000, -10000);
            keepAlive.setAutoRequestFocus(false);
            keepAlive.setFocusableWindowState(false);
            keepAlive.setDefaultCloseOperation(javax.swing.JFrame.DO_NOTHING_ON_CLOSE);
            try {
                keepAlive.setType(java.awt.Window.Type.UTILITY);
            } catch (Exception ignored) {
            }
            try {
                keepAlive.setOpacity(0f);
            } catch (Exception ignored) {
            }
            keepAlive.setVisible(true);
            keepAlive.toBack();
            new javax.swing.Timer(200, ae -> {
            }).start();
        });

        tray.showMessage("Whisper 语音输入",
                "已就绪。" + (config.isLongPressMode() ? "长按 " : "点击 ")
                        + KeyCodeMapper.getNativeKeyText(config.getHotkeyCode())
                        + " 键说话，松开后自动输入。",
                java.awt.TrayIcon.MessageType.INFO);

        // 启动时显示主设置窗口
        openSettings();
    }

    private File getBaseDir() {
        try {
            File javaHome = new File(System.getProperty("java.home"));
            if ("runtime".equals(javaHome.getName())) {
                return javaHome.getParentFile();
            }
            File jarFile = new File(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (jarFile.isFile()) {
                File parent = jarFile.getParentFile();
                if (parent != null && "app".equals(parent.getName())) {
                    return parent.getParentFile();
                }
                return parent;
            }
        } catch (Exception e) {
            System.err.println("获取基目录失败: " + e.getMessage());
        }
        return new File(System.getProperty("user.dir"));
    }

    private String resolvePath(String path, File baseDir) {
        if (path == null || path.isEmpty()) return path;
        File f = new File(path);
        if (f.isAbsolute()) return path;
        return new File(baseDir, path).getAbsolutePath();
    }

    private synchronized void startRecording() {
        System.out.println("startRecording 被调用, enabled=" + config.isEnabled() + ", running=" + recorder.isRunning());
        if (!config.isEnabled()) {
            return;
        }
        if (recorder.isRunning()) {
            return;
        }
        try {
            recorder.start();
            recordStartMs = System.currentTimeMillis();
            tray.setState(TrayManager.State.RECORDING);
            if (config.isSoundFeedback()) {
                playBeep(880, 80);
            }
        } catch (LineUnavailableException e) {
            tray.setState(TrayManager.State.ERROR);
            tray.showMessage("录音失败", "麦克风不可用或被占用: " + e.getMessage(),
                    java.awt.TrayIcon.MessageType.ERROR);
            worker.submit(() -> {
                sleep(2000);
                tray.setState(TrayManager.State.IDLE);
            });
        }
    }

    private synchronized void stopAndRecognize() {
        System.out.println("stopAndRecognize 被调用, enabled=" + config.isEnabled() + ", running=" + recorder.isRunning());
        if (!config.isEnabled() || !recorder.isRunning()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - recordStartMs;
        if (elapsed < config.getMinRecordingMs()) {
            recorder.cancel();
            tray.setState(TrayManager.State.IDLE);
            return;
        }
        byte[] pcm = recorder.stop();
        if (config.isSoundFeedback()) {
            playBeep(660, 60);
        }
        tray.setState(TrayManager.State.RECOGNIZING);
        worker.submit(() -> recognizeAndInput(pcm));
    }

    private synchronized void handleClickToggle() {
        if (!config.isEnabled()) {
            hotkey.resetClickState();
            return;
        }
        try {
            if (recorder.isRunning()) {
                stopAndRecognize();
            } else {
                startRecording();
                return;
            }
        } catch (Exception e) {
        }
        hotkey.resetClickState();
    }

    private void recognizeAndInput(byte[] pcm) {
        System.out.println("开始识别, PCM 数据长度: " + (pcm != null ? pcm.length : 0));
        String text = null;
        try {
            text = recognizer.recognize(pcm);
            System.out.println("识别结果: " + text);
        } catch (Exception e) {
            System.err.println("识别异常: " + e.getMessage());
            e.printStackTrace();
            tray.showMessage("识别失败", e.getMessage(), java.awt.TrayIcon.MessageType.WARNING);
        }

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            if (text.isEmpty()) {
                tray.setState(TrayManager.State.ERROR);
                tray.showMessage("未识别到语音", "未识别到有效语音内容",
                        java.awt.TrayIcon.MessageType.WARNING);
                sleep(2000);
                tray.setState(TrayManager.State.IDLE);
                return;
            }
            addHistory(text);
            sleep(60);
            inputEngine.input(text);
            tray.setState(TrayManager.State.IDLE);
        } else {
            tray.setState(TrayManager.State.ERROR);
            tray.showMessage("未识别到语音", "未识别到有效语音内容",
                    java.awt.TrayIcon.MessageType.WARNING);
            sleep(2000);
            tray.setState(TrayManager.State.IDLE);
        }
    }

    private synchronized void toggleEnabled() {
        boolean nowEnabled = !config.isEnabled();
        config.setEnabled(nowEnabled);
        config.save();
        tray.setEnabled(nowEnabled);
        tray.showMessage("Whisper 语音输入",
                nowEnabled ? "已启用语音输入" : "已禁用语音输入",
                java.awt.TrayIcon.MessageType.INFO);
    }

    private void openSettings() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (settingsWindow == null) {
                settingsWindow = new SettingsWindow(config, this::onConfigSaved);
                settingsWindow.setOnExitCallback(this::exit);
            }
            settingsWindow.setVisible(true);
            settingsWindow.toFront();
            settingsWindow.requestFocus();
        });
    }

    private void onConfigSaved() {
        hotkey.updateHotkey(config.getHotkeyCode(), config.getHotkeyModifiers(), config.isLongPressMode());
        if (recorder != null) {
            recorder.setInputDeviceName(config.getAudioInputDevice());
        }
        tray.showMessage("Whisper 语音输入", "设置已保存",
                java.awt.TrayIcon.MessageType.INFO);
    }

    private void switchModel() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            String path = (String) JOptionPane.showInputDialog(
                    null,
                    "请输入新的 Whisper 模型文件路径（ggml-small.bin）：",
                    "切换模型",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    config.getModelPath());
            if (path != null && !path.trim().isEmpty()) {
                try {
                    recognizer.close();
                    String resolvedModel = resolvePath(path.trim(), baseDir);
                    recognizer = new WhisperRecognizer(
                            resolvedWhisperCliPath, resolvedModel, config.getSampleRate());
                    config.setModelPath(path.trim());
                    config.save();
                    tray.showMessage("切换模型", "模型已切换为:\n" + path,
                            java.awt.TrayIcon.MessageType.INFO);
                } catch (Exception e) {
                    tray.showMessage("切换模型失败", e.getMessage(),
                            java.awt.TrayIcon.MessageType.ERROR);
                }
            }
        });
    }

    private void showHistory() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JTextArea area = new JTextArea(20, 40);
            area.setEditable(false);
            if (history.isEmpty()) {
                area.setText("（暂无识别历史）");
            } else {
                StringBuilder sb = new StringBuilder();
                int idx = 1;
                List<String> snapshot = new ArrayList<>(history);
                for (int i = snapshot.size() - 1; i >= 0; i--) {
                    sb.append(idx++).append(". ").append(snapshot.get(i)).append("\n");
                }
                area.setText(sb.toString());
            }
            JOptionPane.showMessageDialog(null, new JScrollPane(area),
                    "识别历史（最近 " + HISTORY_LIMIT + " 条）",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private synchronized void exit() {
        worker.submit(() -> {
            try {
                if (recorder.isRunning()) {
                    recorder.cancel();
                }
                if (recognizer != null) {
                    recognizer.close();
                }
                if (hotkey != null) {
                    hotkey.stop();
                }
                worker.shutdown();
            } catch (Exception ignored) {
            }
            if (tray != null) {
                tray.remove();
            }
            System.exit(0);
        });
    }

    private void addHistory(String text) {
        synchronized (history) {
            history.addLast(text);
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkAudioInputAvailable() {
        try {
            java.util.List<javax.sound.sampled.Mixer.Info> devices =
                    com.whispertype.audio.AudioRecorder.listInputDevices(recorder.getFormat());
            if (devices.isEmpty()) {
                javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "未检测到任何支持当前音频格式（" + config.getSampleRate() + "Hz / "
                                + config.getSampleSizeBits() + "bit / " + config.getChannels()
                                + "声道）的录音设备。\n\n"
                                + "请连接麦克风后在「设置 - 音频输入设备」中选择设备，"
                                + "或检查系统音频设置。",
                        "Whisper 语音输入 - 音频设备警告",
                        JOptionPane.WARNING_MESSAGE));
                return;
            }
            String cfgDev = config.getAudioInputDevice();
            if (cfgDev != null && !cfgDev.isEmpty()) {
                boolean found = false;
                for (javax.sound.sampled.Mixer.Info mi : devices) {
                    if (cfgDev.equals(mi.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                            "配置的音频输入设备已失效：\n  " + cfgDev
                                    + "\n\n将回退到系统默认录音设备。"
                                    + "请在「设置 - 音频输入设备」中重新选择。",
                            "Whisper 语音输入 - 设备已失效",
                            JOptionPane.WARNING_MESSAGE));
                    config.setAudioInputDevice("");
                    config.save();
                    recorder.setInputDeviceName("");
                }
            }
        } catch (Exception e) {
            System.err.println("音频设备检查异常: " + e.getMessage());
        }
    }

    private void playBeep(int freq, int ms) {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                int total = (int) (format.getSampleRate() * (ms / 1000.0));
                byte[] buf = new byte[total * 2];
                for (int i = 0; i < total; i++) {
                    double angle = 2.0 * Math.PI * freq * i / format.getSampleRate();
                    short val = (short) (Short.MAX_VALUE * 0.25 * Math.sin(angle));
                    buf[i * 2] = (byte) (val & 0xff);
                    buf[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
                }
                line.write(buf, 0, buf.length);
                line.drain();
            }
        } catch (Exception e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}