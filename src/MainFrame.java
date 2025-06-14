import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private JTree directoryTree;
    private ThumbnailPanel thumbnailPanel;
    private JLabel infoLabel;
    public MainFrame() {
        // 先构建目录树再初始化UI组件
        buildDirectoryTree();
        initUI();
    }

    private void initUI() {
        setTitle("图片管理程序");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 目录树区域（移到buildDirectoryTree之后）
        JScrollPane treeScroll = new JScrollPane(directoryTree);
        treeScroll.setPreferredSize(new Dimension(200, 0));
        add(treeScroll, BorderLayout.WEST);

        // 缩略图区域
        thumbnailPanel = new ThumbnailPanel(info -> infoLabel.setText(info));
        add(new JScrollPane(thumbnailPanel), BorderLayout.CENTER);

        // 提示信息区域
        infoLabel = new JLabel("就绪");
        add(infoLabel, BorderLayout.SOUTH);
        JButton slideShowBtn = new JButton("幻灯片播放");
        slideShowBtn.addActionListener(e -> {
            File currentDir = thumbnailPanel.getCurrentDirectory();
            if (currentDir != null) {
                List<File> images = thumbnailPanel.getImageFiles();
                if (!images.isEmpty()) {
                    new SlideShowDialog(MainFrame.this, images, 0).setVisible(true);
                }
            }
        });
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.add(slideShowBtn);
        add(topPanel, BorderLayout.NORTH);

    }

    private void buildDirectoryTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("计算机");
        File[] roots = File.listRoots();
        if (roots == null) {
            JOptionPane.showMessageDialog(this, "无法获取磁盘根目录", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        for (File rootDir : roots) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileNode(rootDir));
            root.add(node);
            loadSubDirectories(node); // 加载第一层子目录
        }

        directoryTree = new JTree(root);
        directoryTree.setCellRenderer(new DirectoryTreeCellRenderer()); // 应用自定义渲染器
        directoryTree.setShowsRootHandles(true);
        directoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // 添加目录选择监听器
        directoryTree.addTreeSelectionListener(e -> {
            TreePath path = e.getPath();
            if (path != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                FileNode fileNode = (FileNode) selectedNode.getUserObject();
                thumbnailPanel.loadImages(fileNode.getFile());
            }
        });
        directoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
    @Override
    public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        TreePath path = event.getPath();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getChildCount() == 1 && node.getChildAt(0).toString().equals("Loading...")) {
            node.removeAllChildren(); // 移除占位节点
            File dir = ((FileNode) node.getUserObject()).getFile();
            loadSubDirectories(node); // 真实加载子目录
            ((DefaultTreeModel) directoryTree.getModel()).reload(node);
        }
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {}
});

    }

    private void loadSubDirectories(DefaultMutableTreeNode parent) {
        File dir = ((FileNode) parent.getUserObject()).getFile();
        File[] children = dir.listFiles();
        if (children != null) {
            // 先添加子目录
            for (File childFile : children) {
                if (childFile.isDirectory() && childFile.canRead()) {
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new FileNode(childFile));
                    childNode.add(new DefaultMutableTreeNode("Loading...")); // 占位节点
                    parent.add(childNode);
                    System.out.println("添加目录节点: " + childFile.getAbsolutePath());
                }
            }
            // 添加所有层级的图片文件并记录完整路径
            for (File childFile : children) {
                if (isImageFile(childFile)) {
                    parent.add(new DefaultMutableTreeNode(new FileNode(childFile)));
                    System.out.println("添加图片文件: " + childFile.getAbsolutePath());
                }
            }
            // 安全刷新树节点显示
            if (directoryTree != null) {
                TreeModel model = directoryTree.getModel();
                if (model instanceof DefaultTreeModel) {
                    ((DefaultTreeModel) model).reload(parent);
                }
            }
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".png") || name.endsWith(".gif")
            || name.endsWith(".bmp");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

class FileNode {
    private File file;
    public FileNode(File file) { this.file = file; }
    public File getFile() { return file; }
    public boolean isDirectory() { return file.isDirectory(); }
    @Override
    public String toString() { return file.getName(); }
}

// 自定义目录树节点渲染器
class DirectoryTreeCellRenderer extends DefaultTreeCellRenderer {
    private Icon directoryIcon = UIManager.getIcon("FileView.directoryIcon");
    private Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
        boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof FileNode) {
            FileNode fileNode = (FileNode) node.getUserObject();
            setIcon(fileNode.isDirectory() ? directoryIcon : fileIcon);
        }
        return this;
    }
}
