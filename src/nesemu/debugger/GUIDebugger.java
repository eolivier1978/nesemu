package nesemu.debugger;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nesemu.engine.Launcher;

public class GUIDebugger extends ADebugger
{
	public JFrame debug_frame;
	
	public JPanel debug_panel;
	public Font debug_font;
	public Font small_debug_font;
	public int font_size;
	public int small_font_size;
	
	protected int key_pressed = 0;

	private int milliseconds_passed = 0;
	public int update_every_x_milliseconds = 0;
	
	public GUIDebugger(Launcher nes_emu_runner)
	{
		super(nes_emu_runner);
		font_size = 10;
		small_font_size = 8;
		debug_panel = new JPanel();
		debug_panel.setLayout(null);
		debug_panel.setBackground(Color.BLUE);
		debugInGUI();
	}
	
	public JLabel addLabel(String text, int x, int y, Color c)
	{
		JLabel label = new JLabel(text+"0");
		label.setFont(debug_font);
		label.setForeground(c);
		label.setDoubleBuffered(true);
		debug_panel.add(label);
		label.setBounds(
				x, y, 
				label.getPreferredSize().width,
				label.getPreferredSize().height);
		label.setText(text);
		
		return label;
	}
	
	public JLabel addLabelSmall(String text, int x, int y, Color c)
	{
		JLabel label = new JLabel(text+"0");
		label.setFont(small_debug_font);
		label.setForeground(c);
		label.setDoubleBuffered(true);
		debug_panel.add(label);
		label.setBounds(
				x, y, 
				label.getPreferredSize().width,
				label.getPreferredSize().height);
		label.setText(text);
		
		return label;
	}
	
	public JLabel addLabel(String text, int x, int y)
	{
		return addLabel(text, x, y, Color.WHITE);
	}
	
	public JLabel addLabelSmall(String text, int x, int y)
	{
		return addLabelSmall(text, x, y, Color.WHITE);
	}
	
	public void openDebuggerWindow()
	{
		debug_font = new Font("Courier New", Font.BOLD, font_size);
		small_debug_font = new Font("Courier New", Font.BOLD, small_font_size);
	}
	
	public void updateDebugger()
	{
	}
	
	public boolean shouldUpdateDebugger()
	{
		milliseconds_passed += 10;
		if (milliseconds_passed >= update_every_x_milliseconds)
		{
			milliseconds_passed = 0;
			return true;
		}
		return false;
	}
	
	public void debugInGUI()
	{
		openDebuggerWindow();
	}
}
