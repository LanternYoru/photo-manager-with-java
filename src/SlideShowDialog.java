import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

public class SlideShowDialog extends JDialog {
    private List<File> imageFiles;
    private int currentIndex;
    private JLabel imageLabel;
    private float scaleFactor = 1.0f;
    private Timer timer;

    public SlideShowDialog(JFrame parent, List<File> imageFiles, int startIndex) {
        super(parent, "幻灯片播放", true);
        this.imageFiles = imageFiles;
        this.currentIndex = startIndex;
        initUI();
    }

    private void initUI() {
        setSize(800, 600);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());

        // 图片展示区域
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        updateImage();
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        // 操作栏
        JPanel controlPanel = new JPanel(new FlowLayout());
        JButton btnPrev = new JButton("←");
        JButton btnNext = new JButton("→");
        JButton btnZoomIn = new JButton("放大");
        JButton btnZoomOut = new JButton("缩小");
        JButton btnPlay = new JButton("播放");
        JButton btnStop = new JButton("停止");

        // 按钮事件绑定
        btnPrev.addActionListener(e -> showImage(currentIndex - 1));
        btnNext.addActionListener(e -> showImage(currentIndex + 1));
        btnZoomIn.addActionListener(e -> zoomImage(1.2f));
        btnZoomOut.addActionListener(e -> zoomImage(0.8f));
        btnPlay.addActionListener(e -> startAutoPlay());
        btnStop.addActionListener(e -> stopAutoPlay());

        controlPanel.add(btnPrev);
        controlPanel.add(btnNext);
        controlPanel.add(btnZoomIn);
        controlPanel.add(btnZoomOut);
        controlPanel.add(btnPlay);
        controlPanel.add(btnStop);
        add(controlPanel, BorderLayout.SOUTH);

        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAutoPlay();
            }
        });
    }

    private void updateImage() {
        if (imageFiles.isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText("无可用图片");
            return;
        }

        try {
            File file = imageFiles.get(currentIndex);
            BufferedImage original = ImageIO.read(file);
            Image scaled = original.getScaledInstance(
                (int)(original.getWidth() * scaleFactor),
                (int)(original.getHeight() * scaleFactor),
                Image.SCALE_SMOOTH
            );
            imageLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            imageLabel.setText("图片加载失败");
        }
    }

    private void showImage(int newIndex) {
        if (imageFiles.isEmpty()) return;

        if (newIndex < 0) {
            JOptionPane.showMessageDialog(this, "已经是第一张图片");
            return;
        }
        if (newIndex >= imageFiles.size()) {
            JOptionPane.showMessageDialog(this, "已经是最后一张图片");
            return;
        }

        currentIndex = newIndex;
        updateImage();
    }

    private void zoomImage(float factor) {
        scaleFactor *= factor;
        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f)); // 限制缩放范围
        updateImage();
    }

    private void startAutoPlay() {
        if (timer == null) {
            timer = new Timer(1000, e -> showImage(currentIndex + 1));
            timer.start();
        }
    }

    private void stopAutoPlay() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}