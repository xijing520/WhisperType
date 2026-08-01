package com.whispertype.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

public class AppConfig {

    private static final String CONFIG_DIR = System.getProperty("user.home")
            + File.separator + ".whispertype";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "config.json";

    public static final String SYSTEM_MODEL_DIR =
            System.getenv("PROGRAMDATA") + File.separator + "WhisperType" + File.separator + "model";
    public static final String SYSTEM_MODEL_PATH =
            SYSTEM_MODEL_DIR + File.separator + "ggml-small.bin";

    private int hotkeyCode = 58;
    private int hotkeyModifiers = 0;
    private boolean longPressMode = true;

    private int minRecordingMs = 300;
    private int sampleRate = 16000;
    private int sampleSizeBits = 16;
    private int channels = 1;
    private String audioInputDevice = "";

    private String modelPath = SYSTEM_MODEL_PATH;
    private String whisperCliPath = "whisper" + File.separator + "whisper-cli.exe";
    private int recognizeTimeoutMs = 5000;

    private boolean pasteMode = true;
    private String textSuffix = "";
    private boolean trimWhitespace = true;
    private boolean autoSendMode = false;

    private boolean soundFeedback = true;
    private boolean showFloatingWindow = true;

    private boolean darkTheme = false;

    private boolean enabled = true;

    private int configVersion = 0;

    public static AppConfig load() {
        File file = new File(CONFIG_FILE);
        ObjectMapper mapper = new ObjectMapper();
        if (file.exists()) {
            try {
                AppConfig cfg = mapper.readValue(file, AppConfig.class);
                return cfg;
            } catch (IOException e) {
                System.err.println("配置文件解析失败，使用默认配置: " + e.getMessage());
            }
        }
        return new AppConfig();
    }

    public void save() {
        this.configVersion = 1;
        File dir = new File(CONFIG_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("无法创建配置目录: " + CONFIG_DIR);
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(new File(CONFIG_FILE), this);
        } catch (IOException e) {
            System.err.println("保存配置失败: " + e.getMessage());
        }
    }

    public int getHotkeyCode() {
        return hotkeyCode;
    }

    public void setHotkeyCode(int hotkeyCode) {
        this.hotkeyCode = hotkeyCode;
    }

    public int getHotkeyModifiers() {
        return hotkeyModifiers;
    }

    public void setHotkeyModifiers(int hotkeyModifiers) {
        this.hotkeyModifiers = hotkeyModifiers;
    }

    public boolean isLongPressMode() {
        return longPressMode;
    }

    public void setLongPressMode(boolean longPressMode) {
        this.longPressMode = longPressMode;
    }

    public int getMinRecordingMs() {
        return minRecordingMs;
    }

    public void setMinRecordingMs(int minRecordingMs) {
        this.minRecordingMs = minRecordingMs;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getSampleSizeBits() {
        return sampleSizeBits;
    }

    public void setSampleSizeBits(int sampleSizeBits) {
        this.sampleSizeBits = sampleSizeBits;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public String getAudioInputDevice() {
        return audioInputDevice == null ? "" : audioInputDevice;
    }

    public void setAudioInputDevice(String audioInputDevice) {
        this.audioInputDevice = audioInputDevice == null ? "" : audioInputDevice;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getWhisperCliPath() {
        return whisperCliPath;
    }

    public void setWhisperCliPath(String whisperCliPath) {
        this.whisperCliPath = whisperCliPath;
    }

    public int getRecognizeTimeoutMs() {
        return recognizeTimeoutMs;
    }

    public void setRecognizeTimeoutMs(int recognizeTimeoutMs) {
        this.recognizeTimeoutMs = recognizeTimeoutMs;
    }

    public boolean isPasteMode() {
        return pasteMode;
    }

    public void setPasteMode(boolean pasteMode) {
        this.pasteMode = pasteMode;
    }

    public String getTextSuffix() {
        return textSuffix;
    }

    public void setTextSuffix(String textSuffix) {
        this.textSuffix = textSuffix;
    }

    public boolean isTrimWhitespace() {
        return trimWhitespace;
    }

    public void setTrimWhitespace(boolean trimWhitespace) {
        this.trimWhitespace = trimWhitespace;
    }

    public boolean isAutoSendMode() {
        return autoSendMode;
    }

    public void setAutoSendMode(boolean autoSendMode) {
        this.autoSendMode = autoSendMode;
    }

    public boolean isSoundFeedback() {
        return soundFeedback;
    }

    public void setSoundFeedback(boolean soundFeedback) {
        this.soundFeedback = soundFeedback;
    }

    public boolean isShowFloatingWindow() {
        return showFloatingWindow;
    }

    public void setShowFloatingWindow(boolean showFloatingWindow) {
        this.showFloatingWindow = showFloatingWindow;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }
}