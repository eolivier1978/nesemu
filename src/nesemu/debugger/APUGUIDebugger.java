package nesemu.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nesemu.engine.Launcher;
import nesemu.hardware.audio.RP2A03;

public class APUGUIDebugger extends GUIDebugger
{
	private JLabel pulse1MutedLabel;
	private JLabel pulse2MutedLabel;
	private JLabel triangleMutedLabel;
	private JLabel noiseMutedLabel;
	private JLabel dmcMutedLabel;
	
	private JLabel pauseLabel;
	private JLabel invertLabel;
	
	private boolean pulse1_muted = false;
	private boolean pulse2_muted = false;
	private boolean triangle_muted = false;
	private boolean noise_muted = false;
	private boolean dmc_muted = false;
	
	private JLabel pulse1VibratoLabel;
	private JLabel pulse2VibratoLabel;
	
	private int debug_samples_to_keep;
	private RP2A03.DebugSamples pulse1_samples;
	private int pulse1_sample_position;
	private RP2A03.DebugSamples pulse2_samples;
	private int pulse2_sample_position;
	private RP2A03.DebugSamples triangle_samples;
	private int triangle_sample_position;
	private RP2A03.DebugSamples noise_samples;
	private int noise_sample_position;
	private RP2A03.DebugSamples dmc_samples;
	private int dmc_sample_position;
	private RP2A03.DebugSamples overall_samples;
	private int overall_sample_position;
	
	private boolean pause_updates = false;
	public boolean invert_bytes = false;
	
	public APUGUIDebugger(Launcher nes_emu_runner)
	{
		super(nes_emu_runner);
		nes_emu_runner.addDebugger(this);
		debug_samples_to_keep = nes.apu.getDebugSamplesToKeep();
	}
	
	public void openDebuggerWindow()
	{
		super.openDebuggerWindow();
		
		debug_frame = new JFrame("APU Debugger");
		debug_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		debug_frame.setSize(612, 200);
		debug_frame.setLocation(275, 420);
		debug_frame.getContentPane().setBackground(Color.BLUE);
		
		debug_panel = new DebugPanel();
		debug_panel.setLayout(null);
		debug_panel.setBackground(Color.BLUE);
		debug_frame.add(debug_panel, BorderLayout.CENTER);
		
		pulse1MutedLabel = addLabel("P1 Active", 0, 0);
		pulse2MutedLabel = addLabel("P2 Active", 100, 0);
		triangleMutedLabel = addLabel("T Active", 200, 0);
		noiseMutedLabel = addLabel("N Active", 300, 0);
		dmcMutedLabel = addLabel("D Active", 400, 0);
		
		addLabel("Mute/Unmute=1", 0, 112);
		addLabel("Mute/Unmute=2", 100, 112);
		addLabel("Mute/Unmute=3", 200, 112);
		addLabel("Mute/Unmute=4", 300, 112);
		addLabel("Mute/Unmute=5", 400, 112);
		
		pulse1VibratoLabel = addLabel("Vibrato Off", 0, 112+font_size);
		pulse2VibratoLabel = addLabel("Vibrato Off", 100, 112+font_size);
		
		pauseLabel = addLabel("Unpaused", 0, 112+font_size*3);
		invertLabel = addLabel("Not inverted", 0, 112+font_size*4);
		
		debug_frame.setVisible(true);
		debug_frame.validate();
	}
	
	public void updateDebugger()
	{
		if (!pause_updates)
		{
			synchronized(nes.apu)
			{
				pulse1_samples = nes.apu.getPulse1Debug().getCopy();
				pulse2_samples = nes.apu.getPulse2Debug().getCopy();
				triangle_samples = nes.apu.getTriangleDebug().getCopy();
				noise_samples = nes.apu.getNoiseDebug().getCopy();
				dmc_samples = nes.apu.getDMCDebug().getCopy();
				overall_samples = nes.apu.getOverallDebug().getCopy();
			}
			pulse1_sample_position = (pulse1_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			pulse2_sample_position = (pulse2_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			triangle_sample_position = (triangle_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			noise_sample_position = (noise_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			dmc_sample_position = (dmc_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			overall_sample_position = (overall_samples.last_debug_sample_position + 1)%debug_samples_to_keep;
			
			if (nes.apu.pulse1GetVibrato())
			{
				pulse1VibratoLabel.setText("Vibrato ON");
			}
			else
			{
				pulse1VibratoLabel.setText("Vibrato OFF");
			}
			
			if (nes.apu.pulse2GetVibrato())
			{
				pulse2VibratoLabel.setText("Vibrato ON");
			}
			else
			{
				pulse2VibratoLabel.setText("Vibrato OFF");
			}
			
			debug_panel.repaint();
		}
	}
	
	public void NESEmuRunnerFrameDrawn()
	{
		if (shouldUpdateDebugger())
		{
			updateDebugger();
		}
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
	        	
	        	if ((key_pressed == (char)'r') || (key_pressed == (char)'R'))
	        	{
	        		nes.apu.record_sound = !nes.apu.record_sound;
	        	}
	        	
	        	if ((key_pressed == (char)'p') || (key_pressed == (char)'P'))
	        	{
	        		pause_updates = !pause_updates;
	        		if (pause_updates)
	        		{
	        			pauseLabel.setText("Paused");
	        		}
	        		else
	        		{
	        			pauseLabel.setText("Not paused");
	        		}
	        	}
	        	
	        	if ((key_pressed == (char)'i') || (key_pressed == (char)'I'))
	        	{
	        		invert_bytes = !invert_bytes;
	        		nes.apu.setInverted(invert_bytes);
	        		if (invert_bytes)
	        		{
	        			invertLabel.setText("Inverted");
	        		}
	        		else
	        		{
	        			invertLabel.setText("Not inverted");
	        		}
	        	}
	        	
	        	if (key_pressed == (char)'1')
	        	{
	        		pulse1_muted = !pulse1_muted;
	        		nes.apu.setPulse1Muted(pulse1_muted);
	        		if (pulse1_muted)
	        		{
	        			pulse1MutedLabel.setText("P1 Mute");
	        		}
	        		else
	        		{
	        			pulse1MutedLabel.setText("P1 Active");
	        		}
	        	}
	        	
	        	if (key_pressed == (char)'2')
	        	{
	        		pulse2_muted = !pulse2_muted;
	        		nes.apu.setPulse2Muted(pulse2_muted);
	        		if (pulse2_muted)
	        		{
	        			pulse2MutedLabel.setText("P2 Mute");
	        		}
	        		else
	        		{
	        			pulse2MutedLabel.setText("P2 Active");
	        		}
	        	}
	        	
	        	if (key_pressed == (char)'3')
	        	{
	        		triangle_muted = !triangle_muted;
	        		nes.apu.setTriangleMuted(triangle_muted);
	        		if (triangle_muted)
	        		{
	        			triangleMutedLabel.setText("T Mute");
	        		}
	        		else
	        		{
	        			triangleMutedLabel.setText("T Active");
	        		}
	        	}
	        	
	        	if (key_pressed == (char)'4')
	        	{
	        		noise_muted = !noise_muted;
	        		nes.apu.setNoiseMuted(noise_muted);
	        		if (noise_muted)
	        		{
	        			noiseMutedLabel.setText("N Mute");
	        		}
	        		else
	        		{
	        			noiseMutedLabel.setText("N Active");
	        		}
	        	}
	        	
	        	if (key_pressed == (char)'5')
	        	{
	        		dmc_muted = !dmc_muted;
	        		nes.apu.setDMCMuted(dmc_muted);
	        		if (dmc_muted)
	        		{
	        			dmcMutedLabel.setText("D Mute");
	        		}
	        		else
	        		{
	        			dmcMutedLabel.setText("D Active");
	        		}
	        	}
	        }

	        @Override
	        public void keyReleased(KeyEvent e) 
	        {
	        }
	    });
	}
	
	public class DebugPanel extends JPanel
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 6951345556924416745L;

		public void paint(final Graphics g)
		{
			super.paint(g);
			g.setColor(Color.blue);
			g.fillRect(0, 12, 90, 100);
			g.setColor(Color.white);
			
			// Draw pulse 1 channel
			g.drawLine(0, 12, 90, 12);
			g.drawLine(0, 12, 0, 112);
			g.drawLine(90, 12, 90, 112);
			g.drawLine(0, 112, 90, 112);
			if (pulse1_samples != null)
			{
				int n = (pulse1_sample_position+debug_samples_to_keep-1)% debug_samples_to_keep;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = counter;
					int x2 = counter;
					int y1 = (int)(12+50-pulse1_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-pulse1_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					if (y1 == y2)
					{
						x2++;
					}
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
			
			// Draw pulse 2 channel
			g.drawLine(100, 12, 190, 12);
			g.drawLine(100, 12, 100, 112);
			g.drawLine(190, 12, 190, 112);
			g.drawLine(100, 112, 190, 112);
			if (pulse2_samples != null)
			{
				int n = (pulse2_sample_position+debug_samples_to_keep-1)% debug_samples_to_keep;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = 100+counter;
					int x2 = 100+counter;
					int y1 = (int)(12+50-pulse2_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-pulse2_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					if (y1 == y2)
					{
						x2++;
					}
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
			
			// Draw triangle channel
			g.drawLine(200, 12, 290, 12);
			g.drawLine(200, 12, 200, 112);
			g.drawLine(290, 12, 290, 112);
			g.drawLine(200, 112, 290, 112);
			if (triangle_samples != null)
			{
				int n = (triangle_sample_position+debug_samples_to_keep-1)% debug_samples_to_keep;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = 200+counter;
					int x2 = 200+counter;
					int y1 = (int)(12+50-triangle_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-triangle_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					if (y1 == y2)
					{
						x2++;
					}
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
			
			// Draw noise channel
			g.drawLine(300, 12, 390, 12);
			g.drawLine(300, 12, 300, 112);
			g.drawLine(390, 12, 390, 112);
			g.drawLine(300, 112, 390, 112);
			if (noise_samples != null)
			{				
				int n = (noise_sample_position+debug_samples_to_keep-1)% debug_samples_to_keep;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = 300+counter;
					int x2 = 300+counter;
					int y1 = (int)(12+50-noise_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-noise_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					if (y1 == y2)
					{
						x2++;
					}
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
			
			// Draw DMC channel
			g.drawLine(400, 12, 490, 12);
			g.drawLine(400, 12, 400, 112);
			g.drawLine(490, 12, 490, 112);
			g.drawLine(400, 112, 490, 112);
			if (dmc_samples != null)
			{
				int n = (dmc_sample_position+debug_samples_to_keep-1)% debug_samples_to_keep;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = 400+counter;
					int x2 = 400+counter;
					int y1 = (int)(12+50-dmc_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-dmc_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					if (y1 == y2)
					{
						x2++;
					}
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
			
			// Draw overall output
			g.drawLine(500, 12, 590, 12);
			g.drawLine(500, 12, 500, 112);
			g.drawLine(590, 12, 590, 112);
			g.drawLine(500, 112, 590, 112);
			if (overall_samples != null)
			{
				int n = overall_sample_position;
				int counter = 0;
				while (counter < debug_samples_to_keep - 1)
				{
					int x1 = 500+counter;
					int x2 = 500+counter+1;
					int y1 = (int)(12+50-overall_samples.debug_samples[n]*40);
					int y2 = (int)(12+50-overall_samples.debug_samples[(n+1) % debug_samples_to_keep]*40);
					g.drawLine(x1, y1, x2, y2);
					n++;
					n = n % debug_samples_to_keep;
					counter++;
				}
			}
		}
	}
}
