import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class ThumbnailPanel extends JPanel {
    private static final int THUMB_SIZE = 150;
    private File currentDirectory;
    private java.awt.datatransfer.Clipboard clipboard = 
        Toolkit.getDefaultToolkit().getSystemClipboard();

    private List<File> imageFiles = new ArrayList<>();
    private List<Thumbnail> selectedThumbs = new ArrayList<>();
    private InfoUpdater infoUpdater;

    public ThumbnailPanel(InfoUpdater infoUpdater) {
        this.infoUpdater = infoUpdater;
        setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        addMouseListener(new SelectionMouseListener());
        addMouseMotionListener(new SelectionMouseListener());
        
        // 添加右键菜单支持
        // 面板自身的右键菜单（用于空白区域粘贴）
        addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && e.getComponent() instanceof ThumbnailPanel) {
                    createContextMenu(e);
                }
            }
        });
    }

    public void loadImages(File dir) {
        // 添加目录验证和日志
        if (dir == null || !dir.isDirectory() || !dir.canRead()) {
            System.err.println("无效目录: " + (dir != null ? dir.getAbsolutePath() : "null"));
            return;
        }
        System.out.println("正在加载目录: " + dir.getAbsolutePath());
        
        this.currentDirectory = dir;
        removeAll();
        imageFiles.clear();
        selectedThumbs.clear();
        
        File[] files = dir.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return f.isFile() && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".gif")
                || name.endsWith(".bmp"));
        });
        
        if (files != null) {
            // 直接添加文件到列表，确保顺序和数量准确
            for (File file : files) {
                if (addThumbnail(file)) {
                    imageFiles.add(file);
                }
            }
        }
        
        // 强制更新布局和重绘
        revalidate();
        repaint();
        
        // 立即更新信息
        updateInfo(dir);
    }

    private boolean addThumbnail(File file) {
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) {
                return false; // 无效的图片文件
            }
            // 保持宽高比缩放，固定宽度为THUMB_SIZE
            int width = THUMB_SIZE;
            int height = (int) ((double) original.getHeight() / original.getWidth() * THUMB_SIZE);
            Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            Thumbnail thumb = new Thumbnail(new ImageIcon(scaled), file);
            thumb.addMouseListener(new ThumbnailClickListener());
            // 为每个缩略图添加右键菜单支持
            thumb.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        // 选中当前缩略图
                        thumb.setSelected(true);
                        selectedThumbs.clear();
                        selectedThumbs.add(thumb);
                        createContextMenu(e);
                    }
                }
            });
            add(thumb);
            add(Box.createVerticalStrut(10)); // 添加垂直间距
            
            // 添加双击事件监听
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
            return true; // 成功添加缩略图

        } catch (Exception e) {
            e.printStackTrace();
            return false; // 添加缩略图失败
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

    private void createContextMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        
        // 复制菜单项
        JMenuItem copyItem = new JMenuItem("复制");
        copyItem.setEnabled(!selectedThumbs.isEmpty());
        copyItem.addActionListener(evt -> copySelectedFiles());
        menu.add(copyItem);

        // 粘贴菜单项
        JMenuItem pasteItem = new JMenuItem("粘贴");
        pasteItem.addActionListener(evt -> pasteFiles());
        menu.add(pasteItem);

        // 重命名菜单项
        JMenuItem renameItem = new JMenuItem("重命名");
        renameItem.setEnabled(selectedThumbs.size() == 1);
        renameItem.addActionListener(evt -> renameSelectedFile());
        menu.add(renameItem);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void copySelectedFiles() {
        List<File> filesToCopy = new ArrayList<>();
        for (Thumbnail thumb : selectedThumbs) {
            filesToCopy.add(thumb.getFile());
        }
        if (!filesToCopy.isEmpty()) {
            clipboard.setContents(new java.awt.datatransfer.Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.javaFileListFlavor};
                }

                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(DataFlavor.javaFileListFlavor);
                }

                public Object getTransferData(DataFlavor flavor) 
                    throws UnsupportedFlavorException, IOException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return filesToCopy;
                }
            }, null);
            infoUpdater.updateInfo("已复制 " + filesToCopy.size() + " 个文件");
        }
    }

    private void pasteFiles() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                try {
                    if (!clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        return null;
                    }
                    
                    java.util.List<?> fileList = (java.util.List<?>) clipboard.getData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    java.util.List<File> files = new ArrayList<>();
                    for (Object obj : fileList) {
                        if (obj instanceof File) {
                            files.add((File)obj);
                        }
                    }
                    for (File srcFile : files) {
                        if (srcFile.isFile() && isImageFile(srcFile)) {
                            File destFile = getUniqueFileName(srcFile.getName());
                            Files.copy(srcFile.toPath(), destFile.toPath());
                        }
                    }
                    return null;
                } catch (Exception e) {
                    infoUpdater.updateInfo("粘贴失败: " + e.getMessage());
                    return null;
                }
            }

            protected void done() {
                loadImages(currentDirectory);
                infoUpdater.updateInfo("粘贴完成");
            }
        }.execute();
    }

    private File getUniqueFileName(String originalName) {
        String baseName = originalName.replaceFirst("[.][^.]+$", "");
        String extension = originalName.substring(originalName.lastIndexOf('.'));
        int counter = 1;
        File newFile = new File(currentDirectory, originalName);
        
        while (newFile.exists()) {
            String newName = baseName + " (" + counter + ")" + extension;
            newFile = new File(currentDirectory, newName);
            counter++;
        }
        return newFile;
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".png") || name.endsWith(".gif")
            || name.endsWith(".bmp");
    }

    private void renameSelectedFile() {
        if (selectedThumbs.size() != 1) return;
        
        Thumbnail thumb = selectedThumbs.get(0);
        File oldFile = (File) thumb.getFile();
        
        String newName = JOptionPane.showInputDialog(
            this, 
            "输入新文件名：", 
            oldFile.getName()
        );
        
        if (newName == null || newName.trim().isEmpty() || newName.equals(oldFile.getName())) {
            return;
        }
        
        // 检查文件名有效性
        if (newName.contains(File.separator) || newName.matches(".*[\\\\/:*?\"<>|].*")) {
            infoUpdater.updateInfo("无效的文件名");
            return;
        }
        
        // 添加文件扩展名如果用户没有输入
        if (!newName.contains(".")) {
            String ext = getFileExtension(oldFile.getName());
            if (!ext.isEmpty()) {
                newName += "." + ext;
            }
        }

        String finalNewName = newName;
        new SwingWorker<Boolean, Void>() {
            protected Boolean doInBackground() throws Exception {
                File newFile = getUniqueFileName(finalNewName);
                return oldFile.renameTo(newFile);
            }
            
            protected void done() {
                try {
                    if (get()) {
                        loadImages(currentDirectory);
                        infoUpdater.updateInfo("重命名成功");
                    } else {
                        infoUpdater.updateInfo("重命名失败");
                    }
                } catch (Exception ex) {
                    infoUpdater.updateInfo("错误: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
