package nesemu.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nesemu.engine.Launcher;
import nesemu.util.Convert;

public class PPUGUIDebugger extends GUIDebugger
{
	public int nSelectedPalette;
	
	private JLabel paletteLabel;
	private JLabel framerateLabel;
	private JLabel skippedFramesLabel;
	private JLabel repeatedFramesLabel;
	private JLabel totalFramesLabel;
	
	private JLabel[] nametable1_label;
	private JLabel[] nametable2_label;
	
	private JLabel[] oam_label;
	
	BufferedImage patterns1;
	BufferedImage patterns2;
	
	BufferedImage[] palette;
	
	public PPUGUIDebugger(Launcher nes_emu_runner)
	{
		super(nes_emu_runner);
		nes_emu_runner.addDebugger(this);
		nes_emu_runner.video_processor.should_calculate_framerate = true;
	}
	
	public void openDebuggerWindow()
	{
		super.openDebuggerWindow();
		
		nSelectedPalette = 0x00;
		
		patterns1 = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 128; x++)
			for (int y = 0; y < 128; y++)
				patterns1.setRGB(x, y, Color.black.getRGB());
		patterns2 = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 128; x++)
			for (int y = 0; y < 128; y++)
				patterns2.setRGB(x, y, Color.black.getRGB());
		palette = new BufferedImage[8];
		
		nametable1_label = new JLabel[32];
		nametable2_label = new JLabel[32];
		
		oam_label = new JLabel[20];
		
		debug_frame = new JFrame("PPU Debugger");
		debug_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		debug_frame.setSize(600, 800);
		debug_frame.setLocation(890, 14);
		debug_frame.getContentPane().setBackground(Color.BLUE);
		
		debug_panel = new DebugPanel();
		debug_panel.setLayout(null);
		debug_panel.setBackground(Color.BLUE);
		debug_frame.add(debug_panel, BorderLayout.CENTER);
		
		int offset = font_size*15;
		paletteLabel = addLabel("Palette: " + nSelectedPalette, 265, 0);
		totalFramesLabel = addLabel("Total frames:     ", 265, font_size);
		framerateLabel = addLabel("Frame rate:           ", 265, 2*font_size);
		skippedFramesLabel = addLabel("Skipped frames:           ", 265, 3*font_size);
		repeatedFramesLabel = addLabel("Repeated frames:           ", 265, 4*font_size);
		
		for (int n=0; n < 32; n++)
		{
			nametable1_label[n] = 
				addLabelSmall("000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000     ", 
					0, offset + n*small_font_size);
		}
		for (int n=0; n < 32; n++)
		{
			nametable2_label[n] =
				addLabelSmall("000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000     ",
					0, offset + (33+n)*small_font_size);
		}
		
		for (byte n=0; n < 20; n++)
		{
			String s = Convert.getHexStringFromByte(n) + ": (" +
				nes.ppu.pOAM[n * 4 + 3] +
				", " + nes.ppu.pOAM[n * 4 + 0] + ") " +
				"ID: " + Convert.getHexStringFromByte((byte)nes.ppu.pOAM[n * 4 + 1]) +
				" AT: " + Convert.getHexStringFromByte((byte)nes.ppu.pOAM[n * 4 + 2]) + "    ";
			oam_label[n] = addLabelSmall(s, 0, offset + (66+n)*small_font_size);
		}
		
		addLabel("P = Inc Palette", 265, 135);
		
		debug_frame.setVisible(true);
		debug_frame.validate();
	}
	
	public void updateDebugger()
	{
		totalFramesLabel.setText("Total frames: "+nes_emu_runner.video_processor.current_frame_number_video_processor);
		float framerate = (float)Math.round(nes_emu_runner.video_processor.current_frame_rate * 100) / 100;
		framerateLabel.setText("Frame rate: "+framerate);
		skippedFramesLabel.setText("Skipped frames: "+nes_emu_runner.video_processor.skipped_frames);
		repeatedFramesLabel.setText("Repeated frames: "+nes_emu_runner.video_processor.repeated_frames);
		
		patterns1 = nes.ppu.GetPatternTable(0, nSelectedPalette).draw();
		patterns2 = nes.ppu.GetPatternTable(1, nSelectedPalette).draw();
		for (int n=0; n < 8; n++)
		{
			palette[n] = nes.ppu.GetPalette(n);
		}
		
		String temp = "";
		int line = 0;
		for (int n = 0; n < nes.ppu.name_table[0].length; n++)
		{
			if (nes.ppu.name_table[0][n] < 10)
			{
				temp += "00" + nes.ppu.name_table[0][n] + " ";
			}
			else if (nes.ppu.name_table[0][n] < 100)
			{
				temp += "0" + nes.ppu.name_table[0][n] + " ";
			}
			else
			{
				temp += nes.ppu.name_table[0][n] + " ";
			}
			if ((n + 1) % 32 == 0)
			{
				nametable1_label[line].setText(temp);
				line++;
				temp = "";
			}
		}
		
		temp = "";
		line = 0;
		for (int n = 0; n < nes.ppu.name_table[1].length; n++)
		{
			if (nes.ppu.name_table[1][n] < 10)
			{
				temp += "00" + nes.ppu.name_table[1][n] + " ";
			}
			else if (nes.ppu.name_table[1][n] < 100)
			{
				temp += "0" + nes.ppu.name_table[1][n] + " ";
			}
			else
			{
				temp += nes.ppu.name_table[1][n] + " ";
			}
			if ((n + 1) % 32 == 0)
			{
				nametable2_label[line].setText(temp);
				line++;
				temp = "";
			}
		}
		
		for (int n=0; n < 20; n++)
		{
			String s = Convert.getHexStringFromByte((byte)n) + ": (" +
				nes.ppu.pOAM[n * 4 + 3] +
				", " + nes.ppu.pOAM[n * 4 + 0] + ") " +
				"ID: " + Convert.getHexStringFromByte((byte)nes.ppu.pOAM[n * 4 + 1]) +
				" AT: " + Convert.getHexStringFromByte((byte)nes.ppu.pOAM[n * 4 + 2]);
			oam_label[n].setText(s);
		}
		
		paletteLabel.setText("Palette = " + nSelectedPalette);
		debug_panel.repaint();
	}
	
	public void NESEmuRunnerFrameDrawn()
	{
		if (shouldUpdateDebugger())
		{
			updateDebugger();
		}
	}
	
	public void NESEmuRunnerCPUInstructionExecuted()
	{
		
	}
	
	public void NESEmuRunnerCPUInstructionStepped()
	{
		updateDebugger();
	}
	
	public void NESEmuRunnerFrameStepped()
	{
		updateDebugger();
	}
	
	public void debugInGUI()
	{
		super.debugInGUI();
		debug_frame.addKeyListener(new KeyListener() 
		{
	        @Override
	        public void keyTyped(KeyEvent e) 
	        {
	        }

	        @Override
	        public void keyPressed(KeyEvent e) 
	        {
	        	key_pressed = e.getKeyCode();
	        	
	        	if ((key_pressed == (char)'p') || (key_pressed == (char)'P'))
	        	{
	        		nSelectedPalette++;
	        		nSelectedPalette &= 0x07;
	        	}
	        }

	        @Override
	        public void keyReleased(KeyEvent e) 
	        {
	        }
	    });
	}

	public static class Sprite
	{
		public int data[][];
		public int width;
		public int height;
		
		public Sprite(int width, int height)
		{
			data = new int[width][height];
			this.width = width;
			this.height = height;
		}
		
		public BufferedImage draw()
		{
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			for (int x=0; x < width; x++)
			{
				for (int y=0; y < height; y++)
				{
					image.setRGB(x, y, data[x][y]);
				}
			}
			return image;
		}
		
		public void drawPartial(BufferedImage image, int x, int y, int ox, int oy, int width, int height)
		{
			for (int i=0; i < width; i++)
			{
				for (int j=0; j < height; j++)
				{
					image.setRGB(x + i, y + j, data[ox + i][oy + j]);
				}
			}
		}
	}
	
	public class DebugPanel extends JPanel
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = -3230311461602059001L;

		public void paint(final Graphics g)
		{
			super.paint(g);
			g.drawImage(patterns1, 2, 3, null);
			g.drawImage(patterns2, 130+2, 3, null);
			for (int n=0; n < 8; n++)
			{
				g.drawImage(palette[n], 8+n*32, 137, null);
			}
			BufferedImage selection = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			selection.setRGB(0, 0, Color.WHITE.getRGB());
			selection.setRGB(1, 0, Color.WHITE.getRGB());
			selection.setRGB(0, 1, Color.WHITE.getRGB());
			selection.setRGB(1, 1, Color.WHITE.getRGB());
			g.drawImage(selection, 8+nSelectedPalette*32+8, 134, null);
		}
	}
}
