import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.TypeVariable;
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
    private int lastSelectedIndex = -1; // 初始化最后选中索引

    private List<File> imageFiles = new ArrayList<>();
    private List<Thumbnail> selectedThumbs = new ArrayList<>();
    private InfoUpdater infoUpdater;

    public ThumbnailPanel(InfoUpdater infoUpdater) {
        this.infoUpdater = infoUpdater;
        setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        addMouseListener(new SelectionMouseListener());
        addMouseMotionListener(new SelectionMouseListener());
        // 添加空白区域点击支持
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    boolean clickedOnThumbnail = false;
                    for (Component comp : getComponents()) {
                        if (comp instanceof Thumbnail && comp.getBounds().contains(e.getPoint())) {
                            clickedOnThumbnail = true;
                            break;
                        }
                    }
                    if (!clickedOnThumbnail) {
                        selectedThumbs.forEach(t -> t.setSelected(false));
                        selectedThumbs.clear();
                        repaint();
                    }
                }
            }
        });
        
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

    private SwingWorker<Void, Thumbnail> currentWorker;
    
    public void loadImages(File dir) {
        // 取消之前的加载任务
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        // 初始化界面（移除加载提示）
        this.currentDirectory = dir;
        removeAll();
        imageFiles.clear();
        selectedThumbs.clear();
        revalidate();
        repaint();

        // 创建后台加载任务
        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File[] files = dir.listFiles(f -> {
                    String name = f.getName().toLowerCase();
                    return f.isFile() && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".png") || name.endsWith(".gif")
                        || name.endsWith(".bmp"));
                });

                if (files != null) {
                    for (File file : files) {
                        if (isCancelled()) return null;
                        
                        try {
                            BufferedImage original = ImageIO.read(file);
                            if (original == null) continue;
                            
                            int width = THUMB_SIZE;
                            int height = (int) ((double) original.getHeight() / original.getWidth() * THUMB_SIZE);
                            Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                            Thumbnail thumb = new Thumbnail(new ImageIcon(scaled), file);
                            // 添加事件监听器
                            thumb.addMouseListener(new ThumbnailClickListener());
                            thumb.addMouseListener(new MouseAdapter() {
                                public void mouseReleased(MouseEvent e) {
                                    if (e.isPopupTrigger()) {
                                        // 添加当前缩略图到选中列表（如果未选中）
                                        if (!thumb.isSelected()) {
                                            thumb.setSelected(true);
                                            selectedThumbs.add(thumb);
                                        }
                                        // 保持其他已选中的缩略图状态不变
                                        createContextMenu(e);
                                    }
                                }
                                
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
                            
                            // 分批发布缩略图
                            publish(thumb);
                            imageFiles.add(file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Thumbnail> chunks) {
                if (isCancelled()) return;
                
                // 增量更新而不是清除全部
                for (Thumbnail thumb : chunks) {
                    add(thumb);
                }
                // 优化刷新频率，每批更新后只刷新一次
                if (!chunks.isEmpty()) {
                    revalidate();
                    repaint();
                }
            }

            @Override
            protected void done() {
                if (!isCancelled()) {
                    updateInfo(dir);
                    // 确保最终刷新界面
                    // 最终刷新界面
                    revalidate();
                    repaint();
                }
            }
            
            private List<Thumbnail> getThumbnails() {
                List<Thumbnail> thumbs = new ArrayList<>();
                for (Component comp : getComponents()) {
                    if (comp instanceof Thumbnail) {
                        thumbs.add((Thumbnail) comp);
                    }
                }
                return thumbs;
            }
        };
        
        currentWorker.execute();
    }

    private boolean addThumbnail(File file) {
        // 此方法已被后台加载机制替代
        return false;
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
                Component[] components = getComponents();
                List<Thumbnail> thumbs = new ArrayList<>();
                for (Component comp : components) {
                    if (comp instanceof Thumbnail) {
                        thumbs.add((Thumbnail) comp);
                    }
                }
                int currentIndex = thumbs.indexOf(thumb);

                if (e.isControlDown()) {
                    // Ctrl+点击：切换选中状态
                    thumb.toggleSelected();
                    if (thumb.isSelected()) {
                        selectedThumbs.add(thumb);
                    } else {
                        selectedThumbs.remove(thumb);
                    }
                } else if (e.isShiftDown() && lastSelectedIndex != -1) {
                    // Shift+点击：范围选择
                    int start = Math.min(lastSelectedIndex, currentIndex);
                    int end = Math.max(lastSelectedIndex, currentIndex);
                    for (int i = start; i <= end; i++) {
                        Thumbnail t = thumbs.get(i);
                        t.setSelected(true);
                        if (!selectedThumbs.contains(t)) {
                            selectedThumbs.add(t);
                        }
                    }
                } else {
                    // 普通点击：单选模式
                    selectedThumbs.forEach(t -> t.setSelected(false));
                    selectedThumbs.clear();
                    thumb.setSelected(true);
                    selectedThumbs.add(thumb);
                    lastSelectedIndex = currentIndex; // 记录最后选中索引
                }

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

        // 删除菜单项
        JMenuItem delItem = new JMenuItem("删除");
        delItem.addActionListener(evt -> delSelectedFiles());
        menu.add(delItem);

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
    private void delSelectedFiles() {
        List<File> filesToDelete = new ArrayList<>();
        for (Thumbnail thumb : selectedThumbs) {
            filesToDelete.add(thumb.getFile());
        }
        if (!filesToDelete.isEmpty()) {
            for (File file : filesToDelete) {
                file.delete();
            }
        }
        loadImages(currentDirectory);
        infoUpdater.updateInfo("删除完成");

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
