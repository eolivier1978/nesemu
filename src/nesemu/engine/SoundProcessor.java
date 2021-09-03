package nesemu.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import nesemu.hardware.bus.NESBus;
import nesemu.hardware.controller.AInputDevice;

/* 
 * The sound processor thread runs the emulation sufficiently long to produce a sound sample. It then sends this sound
 * sample to the sound buffer. This process is repeated until the buffer is full, after which the sound is played. Whilst
 * the sound is played, the emulation is run again to fill the buffer. The buffer level is retrieved from the Java
 * sound libraries and is used as the basis for running the emulation or sleeping.
 * 
 * The sound processor thread also gets input from the input device and will set the controller input of the emulation
 * accordingly.
 */
public class SoundProcessor extends Thread
{
	private NESBus nes;
	private AInputDevice input;
	private Object execution_lock;
	
	private double current_sound_sample;
	private double volume = MAX_VOLUME;
	
	private boolean sound_enabled = true;
	private SourceDataLine line;
	private long line_id;
	private int bytes_written_to_line;
	private int total_bytes_written_to_line;
	
	private static final int INTERNAL_SOUND_BUFFER_SIZE = 1024;
	private static final int LINE_OUT_BUFFER_SIZE =  INTERNAL_SOUND_BUFFER_SIZE * 20;
	private byte[] internal_sound_buffer = new byte[INTERNAL_SOUND_BUFFER_SIZE];
	private int internal_sound_buffer_position = 0;
	
	private double audio_sample_length_millis = (1 / (double)SAMPLE_RATE) * 1000;
	
	// Variables to track where the emulation is in terms of audio time.
	private double audio_time_per_system_sample;
	private double audio_time_per_nes_clock;
	private double audio_time;
	
	private Method nWrite = null;
	private Method nGetBytePosition = null;
	
	// Debug variable
	private boolean fullspeed = false;
	
	public static final int SAMPLE_RATE = 44100;
	public static final int BIT_DEPTH = 16;
	public static final int CHANNELS = 1;
	public static final int MAX_VOLUME = 32767;

	public SoundProcessor(NESBus nes, AInputDevice input, Object execution_lock) throws Exception 
	{
		this.nes = nes;
		this.input = input;
		this.execution_lock = execution_lock;
		
		if (!sound_enabled) volume = 0;
		setSampleFrequency(SAMPLE_RATE);
		
		openSoundOutput();
	}
	
	public void openSoundOutput() throws Exception
	{
		// Open the Java sound library and get a sound "line" object.
		AudioFormat format = new AudioFormat(SAMPLE_RATE, BIT_DEPTH, CHANNELS, true, false);
		DataLine.Info info = 
			new DataLine.Info(SourceDataLine.class, format, LINE_OUT_BUFFER_SIZE * 5);
		if (!AudioSystem.isLineSupported(info))
		{
			throw new Exception("Sound format not supported.");
		}
	    line = (SourceDataLine) AudioSystem.getLine(info);		    
	    line.open(format);
	    
	    // Get the nWrite and nGetBytePosition private native methods via reflection. This is needed because the
	    // Java sound library is crap and direct access is needed to the DirectSound drivers.
	    // The big problem with this of course is that it makes it Windows specific, but I have not found a workaround
	    // for this yet.
	    Class direct_sound_implementation_class = Class.forName("com.sun.media.sound.DirectAudioDevice");
	    Method[] methods = direct_sound_implementation_class.getDeclaredMethods();
	    for (int n=0; n < methods.length; n++)
	    {
	    	if (methods[n].getName().equals("nWrite"))
	    	{
	    		nWrite = methods[n];
	    		nWrite.setAccessible(true);
	    	}
	    	
	    	if (methods[n].getName().equals("nGetBytePosition"))
	    	{
	    		nGetBytePosition = methods[n];
	    		nGetBytePosition.setAccessible(true);
	    	}
	    }
	    
	    // A line ID is needed to call the DirectSound driver methods retrieved above. Unfortunately this is also
	    // private in Java so we need to use reflection to get this as well.
	    Class directdl_sound_implementation_class = Class.forName("com.sun.media.sound.DirectAudioDevice$DirectDL");
	    Field line_id_field = directdl_sound_implementation_class.getDeclaredField("id");
	    line_id_field.setAccessible(true);
	    Long obj = (Long)line_id_field.get(line);
	    line_id = obj.longValue();
	    
	    line.start();
	}
	
	public void setSampleFrequency(int sample_rate)
	{
		audio_time_per_system_sample = 1.0 / (double)sample_rate;
		audio_time_per_nes_clock = (1.0 / (double)5369318.0); // PPU Clock frequency
	}
	
	// Runs the emulation as long as a sample period. For example, if the sound sample rate is 44100 hertz, then
	// the emulation will be run for 1/44100 seconds.
	// The sound sample at that point is then returned for playback.
	// During this process the input device's state is also provided to the emulation.
	public double runToNextSoundSample()
	{
		synchronized (execution_lock)
		{
			while (audio_time < audio_time_per_system_sample)
			{
				nes.clock();
				nes.controller[0] = input.getController1Input();
				nes.controller[1] = input.getController2Input();
				audio_time += audio_time_per_nes_clock;
			};
			audio_time -= audio_time_per_system_sample;
			return nes.apu.getOutputSample();
		}
	}
	
	// Runs to the next sample as explained above, modifies the sample based on the chosen volume and returns it.
	public int getAudioSample()
	{
		current_sound_sample = runToNextSoundSample();
		current_sound_sample *= volume;
		return (int)current_sound_sample;
	}
	
	// Debug method that can be used to run the emulation at full speed to see what framerate can be achieved.
	public void runFullspeed()
	{
		while (true)
		{
			getAudioSample();
		}
	}
	
	// Runs the emulation normally.
	public void runNormally() throws Exception
	{
		// Temporary variables
		int audio_sample;
		byte byte1;
		byte byte2;
		long position;
		
		boolean last_nes_power_state = false;
		
		while (true)
		{
			// If the NES went from OFF to ON or visa versa.
			if (last_nes_power_state != nes.is_powered_on)
			{
				nes.is_starting_up = true;
				synchronized (execution_lock)
				{
					// Disable the sound during to avoid clicks and pops.
					line.close();
					clearInternalSoundBuffer();
					
					// If we changed from an off state to an on state then power on the NES.
					if (nes.is_powered_on)
					{
						// Run the NES a bit to force Java to do its JIT (Just in Time compilation), but do this
						// without sound and video. Doing this will result in a faster run when we enable the
						// NES again, which avoids sound clicks and pops.
						for (int n=0; n < 100000; n++)
						{
							if (nes.is_powered_on) getAudioSample();
						}
						
						if (nes.is_powered_on)
						{
							// Power off the NES and then power it on again now that we have primed the JVM.
							nes.powerOff();
							nes.reset();
							nes.powerOn();
							nes.reset();
						}
					}
					
					// Enable the sound again
					openSoundOutput();
					
					nes.is_starting_up = false;
					
					execution_lock.notify();
				}
			}
			
			if (nes.is_powered_on)
			{
				last_nes_power_state = true;
				//long position = total_bytes_sent - (line.getLongFramePosition() << 1);
				// Get the current byte position in the line's buffer.
				position = -((Long)(nGetBytePosition.invoke(null, new Object[] {line_id, true, 0}))).longValue();
	
				if (position < LINE_OUT_BUFFER_SIZE)
				{
					// If we are getting dangerously close to running out of line buffer, get some more samples so that we 
					// can buffer more.
					audio_sample = getAudioSample();
					
					// Add the sample to an internal buffer first, we don't want to make unnecessary method calls, rather
					// buffer a bit internally and then blast the buffer to the line.
					byte1 = (byte)(audio_sample & 0xFF);
					byte2 = (byte)((audio_sample & 0xFF00) >> 8);
					internal_sound_buffer[internal_sound_buffer_position] = byte1;
					internal_sound_buffer_position++;
					internal_sound_buffer[internal_sound_buffer_position] = byte2;
					internal_sound_buffer_position++;
	
					// Wait for the internal buffer to be full before blasting it to the line buffer.
					if (internal_sound_buffer_position == INTERNAL_SOUND_BUFFER_SIZE)
					{
						total_bytes_written_to_line = 0;
						
						// Blast the internal buffer to the line buffer.
						while (total_bytes_written_to_line < INTERNAL_SOUND_BUFFER_SIZE)
						{
							bytes_written_to_line = 
								((Integer)
									(
										nWrite.invoke
										(
											null, 
											new Object[] 
											{
												line_id, 
												internal_sound_buffer,
												total_bytes_written_to_line,
												internal_sound_buffer_position - total_bytes_written_to_line,
												0,
												1.0f,
												1.0f
											}
										)
									)
								).intValue();
							total_bytes_written_to_line += bytes_written_to_line;
						}
						internal_sound_buffer_position = 0;
					}
				}
				else
				{
					try
					{
						// Wait a bit here whilst the sound is playing and then loop around to get more sound.
						Thread.sleep(1);
					}
					catch (Throwable t)
					{
						
					}
				}
			}
			else
			{
				// If the NES is off we will simulate an old TV screen's "hiss".
				last_nes_power_state = false;
				randomizeInternalSoundBuffer();
				total_bytes_written_to_line = 0;
				
				// Blast the internal buffer to the line buffer.
				while (total_bytes_written_to_line < INTERNAL_SOUND_BUFFER_SIZE)
				{
					bytes_written_to_line = 
						((Integer)
							(
								nWrite.invoke
								(
									null, 
									new Object[] 
									{
										line_id, 
										internal_sound_buffer,
										total_bytes_written_to_line,
										INTERNAL_SOUND_BUFFER_SIZE,
										0,
										1.0f,
										1.0f
									}
								)
							)
						).intValue();
					total_bytes_written_to_line += bytes_written_to_line;
				}
				Thread.sleep((long)(audio_sample_length_millis * INTERNAL_SOUND_BUFFER_SIZE / 2));
			}
		}
	}
	
	public void clearInternalSoundBuffer()
	{
		// Initialize variables
		for (int n=0; n < INTERNAL_SOUND_BUFFER_SIZE; n++)
		{
			internal_sound_buffer[n] = 0;
		}
	}
	
	public void randomizeInternalSoundBuffer()
	{
		// Initialize variables
		for (int n=0; n < INTERNAL_SOUND_BUFFER_SIZE; n++)
		{
			internal_sound_buffer[n] = (byte)(Math.random() * 8 - 4);
		}
	}
	
	public void run()
	{
		clearInternalSoundBuffer();
		
		try
		{
			if (fullspeed)
			{
				runFullspeed();
			}
			else
			{
				runNormally();
			}
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Debug methods
	public byte[] getInternalSoundBuffer()
	{
		return internal_sound_buffer;
	}
	
}
