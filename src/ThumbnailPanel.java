import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class ThumbnailPanel extends JPanel {
    private static final int THUMB_SIZE = 150;
    private File currentDirectory;

    private List<File> imageFiles = new ArrayList<>();
    private List<Thumbnail> selectedThumbs = new ArrayList<>();
    private InfoUpdater infoUpdater;

    public ThumbnailPanel(InfoUpdater infoUpdater) {
        this.infoUpdater = infoUpdater;
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        addMouseListener(new SelectionMouseListener());
        addMouseMotionListener(new SelectionMouseListener());
    }

    public void loadImages(File dir) {
        this.currentDirectory = dir;

        removeAll();
        imageFiles.clear();
        selectedThumbs.clear();
        File[] files = dir.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".gif")
                || name.endsWith(".bmp");
        });
        if (files != null) {
            for (File file : files) {
                addThumbnail(file);
            }
        }
        updateInfo(dir);
        revalidate();
        repaint();
    }

    private void addThumbnail(File file) {
        try {
            BufferedImage original = ImageIO.read(file);
            Image scaled = original.getScaledInstance(THUMB_SIZE, -1, Image.SCALE_SMOOTH);
            Thumbnail thumb = new Thumbnail(new ImageIcon(scaled), file);
            thumb.addMouseListener(new ThumbnailClickListener());
            add(thumb);
            imageFiles.add(file);
            thumb.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { // 双击事件
                        int index = imageFiles.indexOf(file);
                        new SlideShowDialog(
                            (JFrame)SwingUtilities.getWindowAncestor(ThumbnailPanel.this),
                            imageFiles,
                            index
                        ).setVisible(true);
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateInfo(File dir) {
        long totalSize = imageFiles.stream().mapToLong(File::length).sum();
        String info = String.format("目录: %s | 图片数: %d | 总大小: %.2f MB",
                dir.getName(), imageFiles.size(), totalSize / (1024.0 * 1024));
        infoUpdater.updateInfo(info);
    }
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public List<File> getImageFiles() {
        return imageFiles;
    }



    // 缩略图点击事件处理
    private class ThumbnailClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            Thumbnail thumb = (Thumbnail) e.getSource();
            if (SwingUtilities.isLeftMouseButton(e)) {
                if (e.isControlDown()) {
                    thumb.toggleSelected();
                } else {
                    selectedThumbs.forEach(t -> t.setSelected(false));
                    thumb.setSelected(true);
                }
                selectedThumbs.removeIf(t -> !t.isSelected());
                if (thumb.isSelected()) selectedThumbs.add(thumb);
                infoUpdater.updateInfo("选中: " + selectedThumbs.size() + " 张图片");
            }
        }
    }

    // 矩形选区处理
    private class SelectionMouseListener extends MouseAdapter {
        private Point startPoint;

        @Override
        public void mousePressed(MouseEvent e) {
            startPoint = e.getPoint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (startPoint != null) {
                Rectangle rect = new Rectangle(startPoint);
                rect.add(e.getPoint());
                for (Component comp : getComponents()) {
                    if (comp instanceof Thumbnail) {
                        Thumbnail thumb = (Thumbnail) comp;
                        boolean selected = thumb.getBounds().intersects(rect);
                        thumb.setSelected(selected);
                        if (selected && !selectedThumbs.contains(thumb)) {
                            selectedThumbs.add(thumb);
                        } else if (!selected) {
                            selectedThumbs.remove(thumb);
                        }
                    }
                }
                infoUpdater.updateInfo("选中: " + selectedThumbs.size() + " 张图片");
                repaint();
            }
        }
    }

    interface InfoUpdater {
        void updateInfo(String info);
    }
}