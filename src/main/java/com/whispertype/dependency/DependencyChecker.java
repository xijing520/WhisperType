package com.whispertype.dependency;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.whispertype.config.AppConfig;

public class DependencyChecker {

    private static final String DEFAULT_WHISPER_CLI_PATH = "whisper" + File.separator + "whisper-cli.exe";
    private static final String FALLBACK_WHISPER_CLI_PATH = "app" + File.separator + "whisper" + File.separator + "whisper-cli.exe";
    private static final String LOCAL_MODEL_PATH = "model" + File.separator + "ggml-small.bin";

    // 模型下载地址（hf-mirror.com 是 HuggingFace 国内镜像）
    private static final String MODEL_DOWNLOAD_URL =
            "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-small.bin";

    // Whisper 工具包下载地址（GitHub 原始 + 国内镜像，按顺序尝试）
    private static final String[] WHISPER_BIN_ZIP_URLS = {
            "https://ghfast.top/https://github.com/ggerganov/whisper.cpp/releases/download/v1.9.1/whisper-bin-x64.zip",
            "https://gh-proxy.com/https://github.com/ggerganov/whisper.cpp/releases/download/v1.9.1/whisper-bin-x64.zip",
            "https://github.com/ggerganov/whisper.cpp/releases/download/v1.9.1/whisper-bin-x64.zip"
    };

    public static class Result {
        public boolean whisperCliOk;
        public boolean modelOk;
        public String modelPath;
        public String whisperCliPath;
        public String errorMessage;

        public boolean allOk() {
            return whisperCliOk && modelOk;
        }
    }

    private final String configuredModelPath;
    private final String configuredWhisperCliPath;
    private final File baseDir;

    public DependencyChecker(String configuredModelPath, String configuredWhisperCliPath) {
        this(configuredModelPath, configuredWhisperCliPath, new File(System.getProperty("user.dir")));
    }

    public DependencyChecker(String configuredModelPath, String configuredWhisperCliPath, File baseDir) {
        this.configuredModelPath = configuredModelPath;
        this.configuredWhisperCliPath = configuredWhisperCliPath;
        this.baseDir = baseDir;
    }

    public Result check() {
        Result r = new Result();
        r.modelPath = configuredModelPath;
        r.whisperCliPath = configuredWhisperCliPath;

        // 查找 whisper-cli.exe：配置路径 → baseDir/whisper/ → baseDir/app/whisper/
        File whisperCli = new File(configuredWhisperCliPath);
        if (whisperCli.exists() && whisperCli.isFile()) {
            r.whisperCliOk = true;
        } else {
            File defaultCli = new File(baseDir, DEFAULT_WHISPER_CLI_PATH);
            if (defaultCli.exists() && defaultCli.isFile()) {
                r.whisperCliOk = true;
                r.whisperCliPath = defaultCli.getAbsolutePath();
            } else {
                File fallbackCli = new File(baseDir, FALLBACK_WHISPER_CLI_PATH);
                if (fallbackCli.exists() && fallbackCli.isFile()) {
                    r.whisperCliOk = true;
                    r.whisperCliPath = fallbackCli.getAbsolutePath();
                }
            }
        }

        // 查找模型：配置路径 → 系统路径 → baseDir/model/
        File modelFile = new File(configuredModelPath);
        if (modelFile.exists() && modelFile.isFile()) {
            r.modelOk = true;
            r.modelPath = modelFile.getAbsolutePath();
        } else {
            File systemModel = new File(AppConfig.SYSTEM_MODEL_PATH);
            if (systemModel.exists() && systemModel.isFile()) {
                r.modelOk = true;
                r.modelPath = systemModel.getAbsolutePath();
            } else {
                File localModel = new File(baseDir, LOCAL_MODEL_PATH);
                if (localModel.exists() && localModel.isFile()) {
                    r.modelOk = true;
                    r.modelPath = localModel.getAbsolutePath();
                }
            }
        }

        if (!r.whisperCliOk) {
            r.errorMessage = "Whisper 命令行工具 (whisper-cli.exe) 未找到。\n"
                    + "请确认 whisper 文件夹包含 whisper-cli.exe 及其依赖 DLL。";
        }
        if (!r.modelOk) {
            String msg = "Whisper 模型文件 (ggml-small.bin) 未找到。";
            if (r.errorMessage != null) {
                r.errorMessage = r.errorMessage + "\n\n" + msg;
            } else {
                r.errorMessage = msg;
            }
        }

        return r;
    }

    public Result checkWithAutoDownload() {
        Result r = check();
        if (r.allOk()) {
            return r;
        }

        boolean needDownload = showDownloadDialog(r);
        if (!needDownload) {
            return r;
        }

        try {
            if (!r.whisperCliOk) {
                downloadWhisperBinaries();
                File cliFile = new File(baseDir, DEFAULT_WHISPER_CLI_PATH);
                if (cliFile.exists()) {
                    r.whisperCliOk = true;
                    r.whisperCliPath = cliFile.getAbsolutePath();
                }
            }
            if (!r.modelOk) {
                downloadWhisperModel();
                // 检查多个可能的位置
                File modelFile = new File(baseDir, LOCAL_MODEL_PATH);
                if (!modelFile.exists()) {
                    modelFile = new File(AppConfig.SYSTEM_MODEL_PATH);
                }
                if (modelFile.exists()) {
                    r.modelOk = true;
                    r.modelPath = modelFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            r.errorMessage = "下载失败: " + e.getMessage();
        }

        return r;
    }

    private boolean showDownloadDialog(Result r) {
        final boolean[] confirmed = {false};

        try {
            SwingUtilities.invokeAndWait(() -> {
                StringBuilder msg = new StringBuilder();
                msg.append("检测到以下 Whisper 依赖缺失：\n\n");
                if (!r.whisperCliOk) {
                    msg.append("  - whisper-cli.exe (Whisper 命令行工具)\n");
                }
                if (!r.modelOk) {
                    msg.append("  - ggml-small.bin (Whisper 语音识别模型)\n");
                }
                msg.append("\n是否现在下载？\n");
                msg.append("（whisper 工具包约 30MB，模型约 465MB）");

                int choice = JOptionPane.showConfirmDialog(null,
                        msg.toString(),
                        "Whisper 语音输入 - 依赖缺失",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                confirmed[0] = (choice == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            confirmed[0] = false;
        }

        return confirmed[0];
    }

    private void downloadWhisperBinaries() throws IOException {
        File whisperDir = new File(baseDir, "whisper");
        if (!whisperDir.exists() && !whisperDir.mkdirs()) {
            throw new IOException("无法创建 whisper 目录");
        }

        ProgressDialog progress = new ProgressDialog("正在下载 Whisper 工具包",
                "准备下载 whisper.cpp Windows 预编译版本...");
        progress.show();

        File zipFile = new File(whisperDir, "whisper-bin-x64.zip");
        try {
            // 依次尝试多个镜像源
            boolean downloaded = false;
            Exception lastError = null;
            for (int i = 0; i < WHISPER_BIN_ZIP_URLS.length; i++) {
                try {
                    progress.setStatus("正在从镜像源 " + (i + 1) + "/" + WHISPER_BIN_ZIP_URLS.length + " 下载...");
                    downloadFile(WHISPER_BIN_ZIP_URLS[i], zipFile, progress);
                    downloaded = true;
                    break;
                } catch (Exception e) {
                    System.err.println("镜像源 " + (i + 1) + " 下载失败: " + e.getMessage());
                    lastError = e;
                }
            }
            if (!downloaded) {
                throw new IOException("所有镜像源均下载失败: " + (lastError != null ? lastError.getMessage() : "未知错误"));
            }

            progress.setStatus("正在解压 Whisper 工具包...");
            unzip(zipFile, whisperDir);
            if (!zipFile.delete()) {
                System.err.println("警告：未能删除临时 zip 文件: " + zipFile);
            }

            File extracted = findFirstDir(whisperDir, "whisper-bin-x64");
            if (extracted != null) {
                moveAllFilesUp(extracted, whisperDir);
                if (!extracted.delete()) {
                    System.err.println("警告：未能删除临时解压目录");
                }
            }

            File cliFile = new File(whisperDir, "whisper-cli.exe");
            if (!cliFile.exists()) {
                throw new IOException("解压后未找到 whisper-cli.exe");
            }

            SwingUtilities.invokeLater(() -> progress.finish());
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> progress.finish());
            throw e;
        }
    }

    private void downloadWhisperModel() throws IOException {
        // 优先下载到 baseDir/model/ 目录（避免权限问题）
        File modelDir = new File(baseDir, "model");
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            // 回退到系统目录
            modelDir = new File(AppConfig.SYSTEM_MODEL_DIR);
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                throw new IOException("无法创建模型目录: " + modelDir);
            }
        }

        ProgressDialog progress = new ProgressDialog("正在下载 Whisper 模型",
                "准备下载 ggml-small.bin (约 465MB)...");
        progress.show();

        File modelFile = new File(modelDir, "ggml-small.bin");
        try {
            downloadFile(MODEL_DOWNLOAD_URL, modelFile, progress);
            SwingUtilities.invokeLater(() -> progress.finish());
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> progress.finish());
            throw e;
        }
    }

    private void downloadFile(String urlStr, File destFile, ProgressDialog progress)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);  // 30秒连接超时
        conn.setReadTimeout(120000);     // 120秒读取超时（大文件需要更长时间）
        conn.setInstanceFollowRedirects(true);  // 自动跟随重定向
        conn.setRequestProperty("User-Agent", "WhisperType/2.0");
        conn.setRequestProperty("Accept", "*/*");
        conn.connect();

        int resp = conn.getResponseCode();
        if (resp != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + resp + " - 下载失败");
        }
        int total = conn.getContentLength();
        long totalBytes = total > 0 ? total : 0L;

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[16384];  // 16KB 缓冲区，提高下载速度
            int n;
            long downloaded = 0;
            long lastUpdate = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                downloaded += n;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 200) {
                    final long dl = downloaded;
                    final long tot = totalBytes;
                    SwingUtilities.invokeLater(() -> progress.updateProgress(dl, tot));
                    lastUpdate = now;
                }
            }
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                Path destPath = out.getCanonicalFile().toPath();
                Path basePath = destDir.getCanonicalFile().toPath();
                if (!destPath.startsWith(basePath)) {
                    throw new IOException("非法的 zip 条目路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) {
                        throw new IOException("无法创建目录: " + out);
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("无法创建父目录: " + parent);
                    }
                    Files.copy(zis, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private File findFirstDir(File parentDir, String prefix) {
        if (parentDir == null || !parentDir.isDirectory()) return null;
        File[] children = parentDir.listFiles(File::isDirectory);
        if (children == null) return null;
        for (File c : children) {
            if (c.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                return c;
            }
        }
        return null;
    }

    private void moveAllFilesUp(File fromDir, File toDir) throws IOException {
        File[] files = fromDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            File dest = new File(toDir, f.getName());
            if (!f.renameTo(dest)) {
                Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static class ProgressDialog {
        private final JDialog dialog;
        private final JLabel statusLabel;
        private final JProgressBar progressBar;

        ProgressDialog(String title, String initialStatus) {
            dialog = new JDialog((java.awt.Frame) null, title, true);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.setSize(420, 130);
            dialog.setLocationRelativeTo(null);

            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

            statusLabel = new JLabel(initialStatus);
            progressBar = new JProgressBar(0, 100);
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(true);

            panel.add(statusLabel, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);

            dialog.setContentPane(panel);
        }

        void show() {
            SwingUtilities.invokeLater(() -> {
                dialog.setModal(false);
                dialog.setVisible(true);
            });
        }

        void updateProgress(long downloaded, long total) {
            if (total > 0) {
                progressBar.setIndeterminate(false);
                int percent = (int) (downloaded * 100 / total);
                progressBar.setValue(percent);
                progressBar.setString(percent + "%");
                statusLabel.setText(String.format("下载中... %d / %d KB",
                        downloaded / 1024, total / 1024));
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString("下载中...");
                statusLabel.setText("下载中... " + (downloaded / 1024) + " KB");
            }
        }

        void setStatus(String text) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(text);
                progressBar.setIndeterminate(true);
                progressBar.setString(text);
            });
        }

        void finish() {
            SwingUtilities.invokeLater(() -> dialog.dispose());
        }
    }
}