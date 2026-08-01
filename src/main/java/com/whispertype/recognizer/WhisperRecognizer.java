package com.whispertype.recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class WhisperRecognizer implements AutoCloseable {

    private final String whisperCliPath;
    private final String modelPath;
    private final float sampleRate;

    public WhisperRecognizer(String whisperCliPath, String modelPath, float sampleRate) {
        this.whisperCliPath = whisperCliPath;
        this.modelPath = modelPath;
        this.sampleRate = sampleRate;
    }

    public String recognize(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return null;
        }
        File tempWav = null;
        File tempTxt = null;
        try {
            tempWav = File.createTempFile("whisper_input_", ".wav");
            tempWav.deleteOnExit();
            writeWav(tempWav, pcmData, (int) sampleRate);
            tempTxt = new File(tempWav.getAbsolutePath() + ".txt");
            tempTxt.deleteOnExit();
            return runWhisper(tempWav, tempTxt);
        } catch (Exception e) {
            System.err.println("Whisper 识别失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (tempTxt != null) tempTxt.delete();
            if (tempWav != null) tempWav.delete();
        }
    }

    private void writeWav(File wavFile, byte[] pcmData, int sampleRate) throws Exception {
        int dataSize = pcmData.length;
        int fileSize = 44 + dataSize;

        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            fos.write(new byte[]{'R', 'I', 'F', 'F'});
            fos.write(intToLittleEndian(fileSize - 8));
            fos.write(new byte[]{'W', 'A', 'V', 'E'});
            fos.write(new byte[]{'f', 'm', 't', ' '});
            fos.write(intToLittleEndian(16));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(intToLittleEndian(sampleRate));
            fos.write(intToLittleEndian(sampleRate * 2));
            fos.write(shortToLittleEndian((short) 2));
            fos.write(shortToLittleEndian((short) 16));
            fos.write(new byte[]{'d', 'a', 't', 'a'});
            fos.write(intToLittleEndian(dataSize));
            fos.write(pcmData);
        }
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToLittleEndian(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }

    private String runWhisper(File wavFile, File txtFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                whisperCliPath,
                "-m", modelPath,
                "-f", wavFile.getAbsolutePath(),
                "-l", "auto",
                "-otxt",
                "-t", "4"
        );
        pb.redirectErrorStream(true);
        File cliFile = new File(whisperCliPath);
        if (cliFile.getParentFile() != null) {
            pb.directory(cliFile.getParentFile());
        }

        Process process = pb.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() > 0) log.append("\n");
                log.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("whisper-cli 退出码: " + exitCode);
            System.err.println("whisper-cli 输出:\n" + log);
            throw new RuntimeException("whisper-cli 退出码: " + exitCode);
        }

        if (!txtFile.exists()) {
            System.err.println("whisper-cli 未生成输出文件: " + txtFile);
            System.err.println("whisper-cli 输出:\n" + log);
            return null;
        }

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(txtFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (result.length() > 0) result.append("\n");
                result.append(line.trim());
            }
        }

        String text = result.toString().trim();
        if (text.isEmpty() || "[no text]".equals(text) || "(blank audio)".equals(text)) {
            return null;
        }
        return text;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public String getModelPath() {
        return modelPath;
    }

    @Override
    public void close() {
    }
}
