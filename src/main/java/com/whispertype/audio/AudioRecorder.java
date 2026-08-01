package com.whispertype.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频录制模块
 * 使用 javax.sound.sampled.TargetDataLine 采集 16kHz/16bit/单声道 PCM 音频
 *
 * 录音流程：
 *   start() -> 子线程循环读取音频数据 -> stop() -> 返回 PCM 字节数组
 *
 * 设备选择：
 *   支持按 Mixer 名称选择输入设备（与设置界面联动）。
 *   若未指定设备名（null 或空），回退到系统默认录音设备。
 */
public class AudioRecorder {

    private final int sampleRate;
    private final int sampleSizeBits;
    private final int channels;
    /** 指定的输入设备名（null/空 = 系统默认） */
    private String inputDeviceName;

    private TargetDataLine dataLine;
    private Thread captureThread;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long startTimeMs = 0;

    public AudioRecorder(int sampleRate, int sampleSizeBits, int channels) {
        this(sampleRate, sampleSizeBits, channels, null);
    }

    public AudioRecorder(int sampleRate, int sampleSizeBits, int channels, String inputDeviceName) {
        this.sampleRate = sampleRate;
        this.sampleSizeBits = sampleSizeBits;
        this.channels = channels;
        this.inputDeviceName = inputDeviceName;
    }

    public void setInputDeviceName(String name) {
        this.inputDeviceName = name;
    }

    public String getInputDeviceName() {
        return inputDeviceName;
    }

    /**
     * 构造音频格式：PCM signed, little-endian
     * AudioFormat(sampleRate, sampleSizeBits, channels, signed=true, bigEndian=false)
     */
    public AudioFormat getFormat() {
        return new AudioFormat(sampleRate, sampleSizeBits, channels, true, false);
    }

    /**
     * 枚举系统中所有支持目标录音格式的输入设备。
     *
     * @param format 期望的音频格式（null 则不做格式校验，返回所有 TargetDataLine 设备）
     * @return 设备信息列表（Mixer.Info），空列表表示无可用设备
     */
    public static List<Mixer.Info> listInputDevices(AudioFormat format) {
        List<Mixer.Info> result = new ArrayList<>();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            // 该 Mixer 是否支持目标格式的 TargetDataLine
            if (mixer.isLineSupported(lineInfo)) {
                result.add(mixerInfo);
            }
        }
        return result;
    }

    /**
     * 便捷重载：使用本实例的音频格式枚举设备
     */
    public List<Mixer.Info> listInputDevices() {
        return listInputDevices(getFormat());
    }

    /**
     * 开始录音
     * <p>设备选择策略：
     * <ol>
     *   <li>若 {@link #inputDeviceName} 非空且匹配到 Mixer，使用该 Mixer</li>
     *   <li>否则回退到系统默认设备（{@link AudioSystem#getTargetDataLine(AudioFormat)}）</li>
     * </ol>
     *
     * @throws LineUnavailableException 麦克风不可用、被占用或指定设备不存在
     */
    public synchronized void start() throws LineUnavailableException {
        if (running.get()) {
            return;
        }
        AudioFormat format = getFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

        // 按设备名查找 Mixer
        Mixer selectedMixer = null;
        if (inputDeviceName != null && !inputDeviceName.trim().isEmpty()) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().equals(inputDeviceName)) {
                    Mixer m = AudioSystem.getMixer(mi);
                    if (m.isLineSupported(lineInfo)) {
                        selectedMixer = m;
                    }
                    break;
                }
            }
            if (selectedMixer == null) {
                // 指定设备未找到，提示用户并回退默认（在错误信息中体现）
                throw new LineUnavailableException(
                        "指定的音频输入设备不存在或不支持当前格式: " + inputDeviceName
                                + "\n请在设置中重新选择音频输入设备。");
            }
        }

        if (selectedMixer != null) {
            dataLine = (TargetDataLine) selectedMixer.getLine(lineInfo);
        } else {
            // 默认设备
            dataLine = AudioSystem.getTargetDataLine(format);
        }
        dataLine.open(format);
        dataLine.start();

        buffer.reset();
        running.set(true);
        startTimeMs = System.currentTimeMillis();

        // 子线程持续读取音频数据
        captureThread = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (running.get() && dataLine != null) {
                int read = dataLine.read(chunk, 0, chunk.length);
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                }
            }
        }, "WhisperType-AudioCapture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /**
     * 停止录音
     * @return 录制的 PCM 数据
     */
    public synchronized byte[] stop() {
        if (!running.get()) {
            return new byte[0];
        }
        running.set(false);
        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataLine != null) {
            dataLine.stop();
            dataLine.close();
            dataLine = null;
        }
        return buffer.toByteArray();
    }

    /**
     * 当前已录制的 PCM 数据快照（用于流式识别）
     */
    public synchronized byte[] snapshot() {
        return buffer.toByteArray();
    }

    /**
     * 取消录音（丢弃数据）
     */
    public synchronized void cancel() {
        running.set(false);
        if (dataLine != null) {
            dataLine.stop();
            dataLine.close();
            dataLine = null;
        }
        buffer.reset();
    }

    /**
     * 录音时长（毫秒）
     */
    public long getElapsedMs() {
        if (startTimeMs == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    public boolean isRunning() {
        return running.get();
    }
}
