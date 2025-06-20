import javax.swing.*;
import java.awt.*;
import java.io.File;

public class Thumbnail extends JPanel {
    private boolean isSelected = false;
    private static final Color SELECT_COLOR = new Color(0, 150, 255);
    private File file; // 新增文件引用字段

    public Thumbnail(Icon icon, File file) {
        this.file = file; // 保存文件引用
        setLayout(new BorderLayout());
        JLabel imageLabel = new JLabel(icon);
        JLabel nameLabel = new JLabel(file.getName(), SwingConstants.CENTER);
        add(imageLabel, BorderLayout.CENTER);
        add(nameLabel, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(160, 160));
    }

    public File getFile() {
        return file; // 直接返回保存的文件引用
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
        repaint();
    }

    public boolean isSelected() { return isSelected; }

    public void toggleSelected() { setSelected(!isSelected); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (isSelected) {
            g.setColor(SELECT_COLOR);
            ((Graphics2D) g).setStroke(new BasicStroke(3));
            g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
        }
    }


}
