package nesemu.engine;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

import nesemu.hardware.bus.NESBus;
import nesemu.hardware.controller.AInputDevice;
import nesemu.hardware.video.RP2C02;
import nesemu.hardware.video.RP2C02.Frame;

/*
 * The video processor thread aims to draw the frames of the emulation at a constant rate of X frames per second where
 * X is usually 60 (for NTSC).
 * 
 * Note that the video processor thread runs independently of the actual emulation. The emulation stores frames as it
 * executes and the video processor thread draws these frames at the correct time. If this is not done the emulation
 * will speed up and slow down slightly since the emulation is run according to the sound buffer i.e. if the sound
 * buffer is getting low the emulation is run quickly to catch up. It is during these "quick catchup" times that the
 * framerate will then not be at the desirable speed and therefore this video processor thread is required.
 * 
 * Doing it this way also reflects how an actual NES works i.e. the APU and PPU run independently of each other.
 */
public class VideoProcessor extends Thread
{
	private NESBus nes;
	private Object execution_lock;

	// The current frame rate being achieved and associate variables.
	public int current_frame_number_video_processor;
	public double current_frame_rate = 0.0f;
	private int last_total_frames_drawn = 0;
	private long last_framerate_sample_time_nanos;
	
	// Since the video processor and emulation runs independently of each other, they may drift out of sync from time
	// to time. To compensate for this frames have to be skipped (when the emulation is running too quickly) or 
	// repeated (when the emulation is running too slowly). The variables below keep track of this.
	public int skipped_frames = 0;
	public int repeated_frames = 0;
	
	// The actual window that output will be rendered on.
	private TVScreenFrame tv_screen_frame;
	// The graphics context of the window that output will be rendered on.
	private Graphics graphics;
	
	// Debug variable
	public boolean should_calculate_framerate = false;
	
	private static final double ONE_SECOND_MICROS = 1000000;
	private static final double ONE_SECOND_NANOS = 1000000000;
	
	// The target frame rate for NTSC = 60,09847755611226 FPS
	private static final double TARGET_FRAME_RATE = (RP2C02.NTSC_FREQUENCY / RP2C02.PPU_FRAME_CLOCKS) * ONE_SECOND_MICROS;
	
	public VideoProcessor(NESBus nes, AInputDevice input, Object execution_lock)
	{
		this.nes = nes;
		this.execution_lock = execution_lock;
		
		tv_screen_frame = new TVScreenFrame();
		tv_screen_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		tv_screen_frame.setLayout(null);
		tv_screen_frame.setBounds(5, 20, 512, 480);
		tv_screen_frame.setResizable(false);
		tv_screen_frame.setUndecorated(true);
		tv_screen_frame.setVisible(true);
		
		tv_screen_frame.addKeyListener((KeyListener)input);
	}
	
	public double calculateCurrentFrameRate()
	{
		long time_elapsed_nanos = System.nanoTime() - last_framerate_sample_time_nanos;
		if (time_elapsed_nanos >= ONE_SECOND_NANOS)
		{
			// Calculate the average frame rate since the start of the emulation
			current_frame_rate =
				(current_frame_number_video_processor - last_total_frames_drawn) / 
				(time_elapsed_nanos / ONE_SECOND_NANOS);
			
			// Reset the last sample time
			last_framerate_sample_time_nanos = System.nanoTime();
			last_total_frames_drawn = current_frame_number_video_processor;
		}
		return current_frame_rate;
	}
	
	public void run()
	{
		setPriority(MAX_PRIORITY);
		
		while (graphics == null)
		{
			graphics = tv_screen_frame.getGraphics();
			try
			{
				Thread.sleep(10);
			}
			catch (Exception e)
			{
				
			}
		}
		
		// Initialize the current frame variable.
		current_frame_number_video_processor = 0;
		Frame[] current_frame;
		current_frame = new Frame[1];
		current_frame[0] = new Frame();
		current_frame[0].frame = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
		
		// Calculate the time between frames assuming that a frame can be drawn in 0 time.
		double time_between_frames_nanos = (1 / TARGET_FRAME_RATE) * ONE_SECOND_NANOS;
		// Calculate how long the emulation can reliably sleep. 
		int sleep_time_millis = (int)(time_between_frames_nanos / ONE_SECOND_MICROS);
		// Subtract 2 milliseconds, the rest of the sleep time will be in a tight loop to ensure accuracy.
		// Also, Java is not very accurate with Thread.sleep, at best the accuracy is within 3 ms.
		sleep_time_millis -= 3;
		
		// The exact nanosecond the frame should be drawn on. The emulation will try to get as close as possible
		// to this.
		double next_target_time_nanos;
		
		// Variables used by reference to store the current frame number of the APU as 
		int[] current_frame_number_apu = new int[1]; 
		boolean[] frame_retrieved = new boolean[1];
		
		// Whether the NES emulator was on or not.
		boolean last_nes_power_state = false;
		
		try
		{
			next_target_time_nanos = System.nanoTime()+time_between_frames_nanos;
			while (true)
			{
				if (last_nes_power_state != nes.is_powered_on)
				{
					if (nes.is_powered_on)
					{
						current_frame[0].frame = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
						for (int x=0; x < 256; x++)
							for (int y=0; y < 240; y++)
								current_frame[0].frame.setRGB(x, y, RP2C02.NES_GRAY);
						graphics.drawImage(current_frame[0].frame, 0, 0, 512, 480, 0, 0, 256, 240, null);
						synchronized (execution_lock)
						{
							if (nes.is_starting_up)
								execution_lock.wait();
						}
						next_target_time_nanos = System.nanoTime()+time_between_frames_nanos;
						current_frame_number_video_processor = 0;
					}
				}
				if (nes.is_powered_on)
				{
					last_nes_power_state = true;
					
					// Sleep for the calculated milliseconds.
					Thread.sleep(sleep_time_millis);
					
					// Sleep the additional nanoseconds to ensure we start the draw process perfectly on a 
					// 1/60th of a second or whatever the framerate is.
					while (System.nanoTime() < next_target_time_nanos)
					{
						
					}
					
					// Now get and draw the finished frame, hopefully this takes less than 25% of the sleep time.
					synchronized (nes.ppu)
					{
						nes.ppu.getFrame(current_frame_number_video_processor, current_frame, frame_retrieved, current_frame_number_apu);
						if (!frame_retrieved[0])
						{
							current_frame_number_video_processor = current_frame_number_apu[0] - 1;
							repeated_frames++;
						}
						else
						{
							if (current_frame_number_video_processor < current_frame_number_apu[0] - 2)
							{
								current_frame_number_video_processor++;
								skipped_frames++;
							}
						}
					}
					graphics.drawImage(current_frame[0].frame, 0, 0, 512, 480, 0, 0, 256, 240, null);
					if (should_calculate_framerate) calculateCurrentFrameRate();
					current_frame_number_video_processor++;

					// Set the next target time in nanoseconds.
					next_target_time_nanos += time_between_frames_nanos;
				}
				else
				{
					// If the NES is off we will simulate an old TV screen's "snow".
					last_nes_power_state = false;
					for (int x=0; x < 256; x++)
						for (int y=0; y < 240; y++)
							current_frame[0].frame.setRGB(x, y, Math.random() > 0.5 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
					graphics.drawImage(current_frame[0].frame, 0, 0, 512, 480, 0, 0, 256, 240, null);
				}
			}
		}
		catch (Throwable t)
		{
			System.out.println("Video thread crashed! : "+t.getMessage());
			t.printStackTrace();
		}
	}
	
	public class TVScreenFrame extends JFrame
	{
		private Point mouseClickPoint; // Will reference to the last pressing (not clicking) position

		/**
		 * 
		 */
		private static final long serialVersionUID = 2555106564339690224L;
		
		public TVScreenFrame()
		{
		    addMouseListener(new MouseAdapter()
		    {
		        public void mousePressed(MouseEvent e)
		        {
		        	mouseClickPoint = e.getPoint(); // update the position
		        }
		    });

		    addMouseMotionListener(new MouseMotionAdapter()
		    {
		        @Override
		        public void mouseDragged(MouseEvent event)
		        {
		        	if (mouseClickPoint != null)
		        	{
			        	Point newPoint = event.getLocationOnScreen();
			            newPoint.translate(-mouseClickPoint.x, -mouseClickPoint.y); // Moves the point by given values from its location
			            setLocation(newPoint); // set the new location
		        	}
		        }
		    });
		}
	}
}
