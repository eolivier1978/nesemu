package nesemu.engine;

import java.awt.BorderLayout;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import nesemu.debugger.ADebugger;
import nesemu.debugger.APUGUIDebugger;
import nesemu.debugger.CPUGUIDebugger;
import nesemu.debugger.PPUGUIDebugger;
import nesemu.hardware.bus.NESBus;
import nesemu.hardware.cartridge.ACartridge;
import nesemu.hardware.cartridge.Cartridge;
import nesemu.hardware.controller.AInputDevice;
import nesemu.hardware.controller.KeyboardInputDevice;

// The coordinator class that is responsible for starting and managing the emulator engine.
public class Launcher
{
	public NESBus nes;
	
	public JFrame main_frame;
	
	// Debugger variables
	private boolean should_execute = true;
	private ADebugger[] debuggers = new ADebugger[10];
	private int number_of_debuggers = 0;
	private Object execution_lock = new Object();
	
	private APUGUIDebugger apudebug;
	private PPUGUIDebugger ppudebug;
	private CPUGUIDebugger cpudebug;
	
	public AInputDevice input_device;
	public SoundProcessor sound_processor;
	public VideoProcessor video_processor;
	
	public Launcher(NESBus nes, ACartridge cartridge)
	{
		this(nes, true);
	}
	
	public Launcher(NESBus nes, boolean should_execute)
	{
		this.nes = nes;
		this.should_execute = should_execute;
		
		for (int n=0; n < 10; n++)
		{
			debuggers[n] = null;
		}
	}
	
	public void addDebugger(ADebugger debugger)
	{
		for (int n=0; n < 10; n++)
		{
			if (debuggers[n] == null)
			{
				debuggers[n] = debugger;
				number_of_debuggers++;
				return;
			}
		}
	}
	
	public void stopExecution()
	{
		synchronized (execution_lock)
		{
			should_execute = false;
		}
	}
	
	public void startExecution()
	{
		synchronized (execution_lock)
		{
			should_execute = true;
		}
	}
	
	public synchronized boolean isExecuting()
	{
		return should_execute;
	}
	
	private void runFrame()
	{
		synchronized (execution_lock)
		{
			nes.ppu.frame_complete = false;
			for (int n=0; n < number_of_debuggers; n++)
			{
				debuggers[n].NESEmuRunnerFrameDrawn();
			}
		}
	}
	
	private void runCPUInstruction()
	{
		synchronized (execution_lock)
		{
			nes.runCPUInstruction();
		}
	}
	
	public void stepCPUInstruction()
	{
		synchronized (execution_lock)
		{
			should_execute = false;
			runCPUInstruction();
			for (int n=0; n < number_of_debuggers; n++)
			{
				debuggers[n].NESEmuRunnerCPUInstructionStepped();
			}
		}
	}
	
	public void stepFrame()
	{
		synchronized (execution_lock)
		{
			should_execute = false;
			runFrame();
			for (int n=0; n < number_of_debuggers; n++)
			{
				debuggers[n].NESEmuRunnerFrameStepped();
			}
		}
	}
	
	public void toggleExecution()
	{
		synchronized (execution_lock)
		{
			if (should_execute)
			{
				stopExecution();
				for (int n=0; n < number_of_debuggers; n++)
				{
					debuggers[n].NESEmuRunnerCPUInstructionStepped();
				}
			}
			else
			{
				startExecution();
			}
		}
	}
	
	public void reset()
	{
		synchronized (execution_lock)
		{
			nes.reset();
		}
	}
	
	public void initializeSound() throws Exception
	{
		sound_processor = new SoundProcessor(nes, input_device, execution_lock);
		sound_processor.start();
	}
	
	public void initializeVideo()
	{
		video_processor = new VideoProcessor(nes, input_device, execution_lock);
		video_processor.start();
	}
	
	public void initializeInputDevice()
	{
		input_device = new KeyboardInputDevice();
	}
		
	public void updateDebuggers()
	{
		synchronized (execution_lock)
		{
			/*for (int n=0; n < number_of_debuggers; n++)
			{
				debuggers[n].NESEmuRunnerCPUInstructionExecuted();
			}*/
			for (int n=0; n < number_of_debuggers; n++)
			{
				debuggers[n].NESEmuRunnerFrameDrawn();
			}
		}
	}
	
	public class DebugThread extends Thread
	{		
		public void run()
		{
			while (true)
			{
				try
				{
					try
					{
						Thread.sleep(10);
					}
					catch (Exception e)
					{
						
					}
					updateDebuggers();
				}
				catch (Throwable t)
				{
					System.out.println("Debugger thread threw an exception! : " + t.getMessage());
					t.printStackTrace();
				}
			}
		}
	}
	
	public void initializeDebugging()
	{
//		cpudebug = new CPUGUIDebugger(this);
//		cpudebug.update_every_x_milliseconds = 500;
//		cpudebug.disassembleProgram();
		ppudebug = new PPUGUIDebugger(this);
		ppudebug.update_every_x_milliseconds = 500;
//		apudebug = new APUGUIDebugger(this);
//		apudebug.update_every_x_milliseconds = 10;
		(new DebugThread()).start();
	}
	
	public void openMainWindow()
	{
		main_frame = new JFrame("NES Emulator");
		main_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		main_frame.setLocation(575, 14);
		
		JButton load_cartridge_button = new JButton("Insert Cartridge");
		JButton power_on_button = new JButton("Power On");
		JButton power_off_button = new JButton("Power Off");
		power_off_button.setEnabled(false);
		JButton reset_button = new JButton("Reset");
		
		load_cartridge_button.addActionListener(new ActionListener() 
		{

		    @Override
		    public void actionPerformed(ActionEvent e)
		    {
		    	synchronized (execution_lock)
		    	{
		    		FileDialog fd = new FileDialog(main_frame, "Load Cartridge");
		    		fd.setVisible(true);
		    		String file_name = fd.getFile();
		    		if ((fd.getFile() != null) && (fd.getFile().trim() != "") &&
		    			(fd.getDirectory() != null) && (fd.getDirectory().trim() != null))
		    		{
		    			file_name = fd.getDirectory() + fd.getFile();
		    			try
		    			{
		    				ACartridge cartridge = new Cartridge(file_name);
		    				nes.insertCartridge(cartridge);
			    			if (cpudebug != null)
			    			{
			    				cpudebug.disassembleProgram();
			    			}
		    			}
		    			catch (IOException ioe)
		    			{
		    				System.out.println("Could not load cartridge. The following error occurred:");
		    				System.out.println(ioe.getMessage());
		    			}
		    		}
		    	}
		    }
		});
		
		power_on_button.addActionListener(new ActionListener() 
		{

		    @Override
		    public void actionPerformed(ActionEvent e)
		    {
		    	if (!nes.is_powered_on)
	    		{
	    			nes.powerOn();
	    		}
		    	synchronized (execution_lock)
		    	{
		    		nes.reset();
	    			power_on_button.setEnabled(false);
	    			power_off_button.setEnabled(true);
		    	}
		    }
		});
		
		power_off_button.addActionListener(new ActionListener() 
		{
		    @Override
		    public void actionPerformed(ActionEvent e)
		    {
		    	
	    		if (nes.is_powered_on)
	    		{
	    			nes.powerOff();
	    		}
    			synchronized (execution_lock)
		    	{
    				nes.reset();
	    			power_off_button.setEnabled(false);
	    			power_on_button.setEnabled(true);
		    	}
		    }
		});
		
		reset_button.addActionListener(new ActionListener() 
		{
		    @Override
		    public void actionPerformed(ActionEvent e)
		    {
		    	synchronized (execution_lock)
		    	{
		    		nes.reset();
		    		if (apudebug != null)
		    		{
		    			nes.apu.setInverted(apudebug.invert_bytes);
		    		}
		    	}
		    }
		});
		
		JPanel controls_panel = new JPanel();
		controls_panel.add(power_on_button);
		controls_panel.add(power_off_button);
		controls_panel.add(reset_button);
		
		main_frame.add(load_cartridge_button, BorderLayout.NORTH);
		main_frame.add(controls_panel, BorderLayout.SOUTH);
		
		main_frame.pack();
		main_frame.setResizable(false);
		main_frame.setVisible(true);
	}
	
	public void run() throws Exception
	{		
//		ACartridge cartridge = new NoCartridge("");
//		nes.insertCartridge(cartridge);
		
		ACartridge cartridge = new Cartridge("C:\\NesEmu\\roms\\dt.nes");
		nes.insertCartridge(cartridge);
		
		openMainWindow();
		
		initializeInputDevice();
		initializeSound();
		initializeVideo();
		
		initializeDebugging();
	}
}
