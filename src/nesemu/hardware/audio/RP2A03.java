package nesemu.hardware.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import nesemu.hardware.bus.NESBus;

public class RP2A03
{
	// Square Wave Pulse Channel 1
	private boolean pulse1_enabled;
	private int pulse1_sample;
	private PulseSequencer pulse1_sequencer;
	private Envelope pulse1_envelope;
	private LengthCounter pulse1_length_counter;
	private Sweeper pulse1_sweep;
	// Debugging variables
	private DebugSamples pulse_1_debug = new DebugSamples();
	private boolean pulse_1_muted = false;
	private boolean pulse_1_vibrato = false;
	private boolean pulse_1_previously_wrote_low_byte = false;
	
	// Square Wave Pulse Channel 2
	private boolean pulse2_enabled;
	private int pulse2_sample;
	private PulseSequencer pulse2_sequencer;
	private Envelope pulse2_envelope;
	private LengthCounter pulse2_length_counter;
	private Sweeper pulse2_sweep;
	// Debugging variables
	private DebugSamples pulse_2_debug = new DebugSamples();
	private boolean pulse_2_muted = false;
	private boolean pulse_2_vibrato = false;
	private boolean pulse_2_previously_wrote_low_byte = false;
	
	// Triangle Wave Channel
	private boolean triangle_enabled;
	private int triangle_sample;
	private TriangleSequencer triangle_sequencer;
	private LinearCounter triangle_linear_counter;
	private LengthCounter triangle_length_counter;
	// Debugging variables
	private DebugSamples triangle_debug = new DebugSamples();
	private boolean triangle_muted = false;
	
	// Noise Channel
	private boolean noise_enabled;
	private int noise_sample;
	private NoiseSequencer noise_sequencer;
	private Envelope noise_envelope;
	private LengthCounter noise_length_counter;
	// Debugging variables
	private DebugSamples noise_debug = new DebugSamples();
	private boolean noise_muted = false;
	
	// DMC Channel
	private boolean dmc_enabled;
	private boolean dmc_irq_enabled;
	private boolean dmc_loop;
	private int dmc_sample;
	private int dmc_rate;
	private int dmc_sample_address;
	private int dmc_sample_length;
	private DMCMemoryReader dmc_memory_reader;
	private DMCSequencer dmc_sequencer;
	// Debugging variables
	private DebugSamples dmc_debug = new DebugSamples();
	private boolean dmc_muted = false;
	
	// Debugging variables
	private DebugSamples overall_debug = new DebugSamples();
	
	// The table that the length counters will load values from.
	private static final /*unsigned 8bit*/ int dmc_frequency_table[] =
			{ 428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54};
	
	// The table that the length counters will load values from.
	private static final /*unsigned 8bit*/ int length_table[] =
			{  10, 254, 20,  2, 40,  4, 80,  6,
			  160,   8, 60, 10, 14, 12, 26, 14,
	           12,  16, 24, 18, 48, 20, 96, 22,
	          192,  24, 72, 26, 16, 28, 32, 30 };
	
	// The frame counter is controlled by register $4017, and it drives the envelope, sweep, and length units on the pulse, 
	// triangle and noise channels. It ticks approximately 4 times per frame (240Hz NTSC), and executes either a 4 or 5 step 
	// sequence, depending how it is configured. It may optionally issue an IRQ on the last tick of the 4 step sequence.
	private int frame_counter;
	
	// The APU is clocked by the emulator every CPU clock. The variable below counts CPU clock cycles.
	private int cpu_clock_counter;
	
	// The variable below counts CPU clock cycles which is 1/3 of the bus clock cycles above.
	private int cpu_cycle;
	
	private boolean sequencer_mode_is_4_step = true; // as opposed to 5 step
	
	// True if the frame counter should be reset after a write to port $4017
	private boolean should_reset_frame_counter;
	
	// Since the frame counter should only be reset on odd CPU cycles after an even cycle has been processed, the variable
	// below stores whether an even CPU cycle has been processed already.
	private boolean even_cpu_cycle_has_occurred;
	
	// In 5 step mode the sound functional units should be clocked after a write to port $4017. The variable below stores
	// whether this should be done.
	private boolean should_clock_5_step_initial;
	
	private boolean interrupt_inhibit = false;
	
	private NESBus bus;
	
	// The length of each step of the sequencer for the 4-step or 5 step modes. This governs the length of a sound note.
	private static final int STEP1_SEQUENCER_STEPS = (int)(3728.5 * 2);
	private static final int STEP2_SEQUENCER_STEPS = (int)(7456.5 * 2);
	private static final int STEP3_SEQUENCER_STEPS = (int)(11185.5 * 2);
	private static final int STEP4_SEQUENCER_STEPS = (int)(14914.5 * 2);
	private static final int STEP5_SEQUENCER_STEPS = (int)(18640.5 * 2);
	
	private static int _0125_DUTY_CYCLE = 0b01000000;
	private static int _0250_DUTY_CYCLE = 0b01100000;
	private static int _0500_DUTY_CYCLE = 0b01111000;
	private static int _025N_DUTY_CYCLE = 0b10011111;
	
	// Debugging variables
	private static final int DEBUG_SAMPLES_TO_KEEP = 90;
	// Debug file that can be used to write sound samples to.
	private FileOutputStream fos;
	private int current_note = 0;
	// Debug variable that will record sound to a file if set to true.
	public boolean record_sound = false;
	
	public RP2A03()
	{
		reset();
		
		newNoteFile();
	}
	
	public void connectToBus(NESBus bus)
	{
		this.bus = bus;
	}
	
	public void newNoteFile()
	{
		if (record_sound)
		{
			try
			{
				if (fos != null) fos.close();
				File f = new File("C:\\temp\\Notes\\note"+current_note+".txt");
				fos = new FileOutputStream(f);
			}
			catch (IOException io)
			{
				System.out.println("File error: "+io.getMessage());
			}
			current_note++;
		}
	}
	
	public double getOutputSample()
	{
		// After 2A03 reset, the sound channels are unavailable for playback during the
		// first 2048 CPU clocks.
		if (cpu_clock_counter < 2048)
		{
			return 0;
		}
		
		// Mix the channels together using the NES non-linear mixing method.
		
		double pulse_out;
		double tnd_out;
		
//		pulse_1_muted = true;
//		pulse_2_muted = true;
//		triangle_muted = true;
//		noise_muted = true;
		
		// Debugging
		if (pulse_1_muted) pulse1_sample = 0;
		if (pulse_2_muted) pulse2_sample = 0;
		if (triangle_muted) triangle_sample = 0;
		if (noise_muted) noise_sample = 0;
		if (dmc_muted) dmc_sample = 0;
		
		// Mix the 2 pulse channels
		pulse_out = 0;
		if (pulse1_sample + pulse2_sample != 0)
		{
			pulse_out = 95.88 / ((8128.0 / (pulse1_sample + pulse2_sample)) + 100.0);
		}
		
		// Mix the 3 remaining channels
		tnd_out = 0;
		if (triangle_sample + noise_sample + dmc_sample != 0)
		{
			tnd_out = 
				159.79 / 
					(
						(1.0 / 
						(triangle_sample/8227.0 + noise_sample/12241.0 + dmc_sample/22638.0)) 
						+ 100.0
					);
		}
		
//		if (record_sound)
//		{
//			if (fos != null)
//			{
//				try
//				{
//					fos.write((""+pulse1_sample+"\r\n").getBytes());
//				}
//				catch (IOException io)
//				{
//					System.out.println("File error: "+io.getMessage());
//				}
//			}
//		}
		
		// Mix the channels together
		double return_value = 2*(pulse_out + tnd_out)-1;
		
		// Set the volume
		return_value = return_value / 1.0;
		
		// Debugging code start
		synchronized(this)
		{
			overall_debug.addSample(return_value);
		}
		// Debugging code end
		
		return return_value;
//		return 0;
	}
	
	// Communication with Main Bus
	public void cpuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{		
		switch (addr)
		{
		case 0x04000:
			// Extract the first 2 bits that signifies the duty cycle and set the byte representing the output waveform 
			// accordingly.
			switch ((data & 0xC0) >> 6)
			{
			case 0x00: pulse1_sequencer.shift_register_load_value = _0125_DUTY_CYCLE; break;
			case 0x01: pulse1_sequencer.shift_register_load_value = _0250_DUTY_CYCLE; break;
			case 0x02: pulse1_sequencer.shift_register_load_value = _0500_DUTY_CYCLE; break;
			case 0x03: pulse1_sequencer.shift_register_load_value = _025N_DUTY_CYCLE; break;
			}
			pulse1_length_counter.halt = (data & 0x20) != 0;
			pulse1_envelope.loop = pulse1_length_counter.halt;
			
			// The channel can either be set to a constant volume or an envelope can be applied
			pulse1_envelope.constant_volume = (data & 0x10) != 0;
			if (pulse1_envelope.constant_volume)
			{
				// If set to a constant volume, set the volume using the last 4 bits of the value
				pulse1_envelope.volume = (data & 0x0F);
			}
			else
			{
				// If set to an envelope, get the period of the envelope from the last 4 bits.
				// (the period becomes V + 1 quarter frames)
				pulse1_envelope.period = (data & 0x0F) + 1;
			}
			break;
		case 0x4001:
			pulse1_sweep.enabled = (data & 0x80) != 0;
			pulse1_sweep.divider_period = (data & 0x70) >> 4;
			pulse1_sweep.negate = (data & 0x08) != 0;
			pulse1_sweep.shift = data & 0x07;
			pulse1_sweep.reload = true;
			break;
		case 0x4002:
			// Read the low 8 bits into the length_counter.
			// NOTE: Only the low byte of the period should be changed here, retaining the high byte. This is how the NES
			// works and games will often just vary the low byte to generate vibrato on the note.
			int pulse_1_old_period = pulse1_sequencer.period;
			pulse1_sequencer.period = ((pulse1_sequencer.period & 0xFF00) | data);
			if (pulse_1_previously_wrote_low_byte)
			{
				if (pulse_1_old_period != pulse1_sequencer.period)
				{
					pulse_1_vibrato = true;
				}
			}
			pulse_1_previously_wrote_low_byte = true;
			break;
		case 0x4003:
			// If the enabled flag is set
			if (pulse1_enabled)
			{
				// Read the high 3 bits into the timer load value
				pulse1_sequencer.period = (((data & 0x07) << 8) | (pulse1_sequencer.period & 0x00FF));
				
				// The length counter is loaded with entry L of the length table
				pulse1_length_counter.length_counter = length_table[(data & 0xF8) >> 3];
				
				// For pulse channels phase is reset
				pulse1_sequencer.resetPhase();
				
				// The envelope is restarted
				pulse1_envelope.start = true;
				
				// Debugger variables
				pulse_1_vibrato = false;
				pulse_1_previously_wrote_low_byte = false;
			}
			break;
		case 0x4004:
			// Extract the first 2 bits that signifies the duty cycle and set the byte representing the output waveform 
			// accordingly.
			switch ((data & 0xC0) >> 6)
			{
			case 0x00: pulse2_sequencer.shift_register_load_value = _0125_DUTY_CYCLE; break;
			case 0x01: pulse2_sequencer.shift_register_load_value = _0250_DUTY_CYCLE; break;
			case 0x02: pulse2_sequencer.shift_register_load_value = _0500_DUTY_CYCLE; break;
			case 0x03: pulse2_sequencer.shift_register_load_value = _025N_DUTY_CYCLE; break;
			}
			pulse2_length_counter.halt = (data & 0x20) != 0;
			pulse2_envelope.loop = pulse2_length_counter.halt;
			
			// The channel can either be set to a constant volume or an envelope can be applied
			pulse2_envelope.constant_volume = (data & 0x10) != 0;
			if (pulse2_envelope.constant_volume)
			{
				// If set to a constant volume, set the volume using the last 4 bits of the value
				pulse2_envelope.volume = (data & 0x0F);
			}
			else
			{
				// If set to an envelope, get the period of the envelope from the last 4 bits.
				// (the period becomes V + 1 quarter frames)
				pulse2_envelope.period = (data & 0x0F) + 1;
			}
			break;
		case 0x4005:
			pulse2_sweep.enabled = (data & 0x80) != 0;
			pulse2_sweep.divider_period = (data & 0x70) >> 4;
			pulse2_sweep.negate = (data & 0x08) != 0;
			pulse2_sweep.shift = data & 0x07;
			pulse2_sweep.reload = true;
			break;
		case 0x4006:
			// Read the low 8 bits into the length_counter
			// NOTE: Only the low byte of the period should be changed here, retaining the high byte. This is how the NES
			// works and games will often just vary the low byte to generate vibrato on the note.
			int pulse2_old_period = pulse2_sequencer.period;
			pulse2_sequencer.period = ((pulse2_sequencer.period & 0xFF00) | data);
			if (pulse_2_previously_wrote_low_byte)
			{
				if (pulse2_old_period != pulse2_sequencer.period)
				{
					pulse_2_vibrato = true;
				}
			}
			pulse_2_previously_wrote_low_byte = true;
			break;
		case 0x4007:
			// If the enabled flag is set
			if (pulse2_enabled)
			{
				// Read the high 3 bits into the timer load value
				pulse2_sequencer.period = (((data & 0x07) << 8) | (pulse2_sequencer.period & 0x00FF));
				
				// The length counter is loaded with entry L of the length table
				pulse2_length_counter.length_counter = length_table[(data & 0xF8) >> 3];
				
				// For pulse channels phase is reset
				pulse2_sequencer.resetPhase();
				
				// The envelope is restarted
				pulse2_envelope.start = true;
				
				// Debugger variables
				pulse_2_vibrato = false;
				pulse_2_previously_wrote_low_byte = false;
			}
			break;
		case 0x4008:
			// Load the linear counter
			triangle_linear_counter.reload_value = data & 0x7F;
			triangle_linear_counter.control = (data & 0x80) != 0;
			
			// Also set the length counter halt flag
			triangle_length_counter.halt = triangle_linear_counter.control;
			break;
		case 0x400A:
			// Read the low 8 bits into the length_counter
			triangle_sequencer.period = ((triangle_sequencer.period & 0xFF00) | data);
			break;
		case 0x400B:
			triangle_sequencer.period = ((data & 0x07) << 8) | (triangle_sequencer.period & 0x00FF);
		
			triangle_linear_counter.reload = true;
			// Load the length counter
			// The length counter is loaded with entry L of the length table
			triangle_length_counter.length_counter = length_table[(data & 0xF8) >> 3];
			break;
		case 0x400C:
			noise_envelope.constant_volume = (data & 0x10) != 0;
			
			// The channel can either be set to a constant volume or an envelope can be applied
			if (noise_envelope.constant_volume)
			{
				// If set to a constant volume, set the volume using the last 4 bits of the value
				noise_envelope.volume = (data & 0x0F);
			}
			else
			{
				// If set to an envelope, get the period of the envelope from the last 4 bits.
				// (the period becomes V + 1 quarter frames)
				noise_envelope.period = (data & 0x0F) + 1;
			}
			noise_length_counter.halt = (data & 0x20) != 0;
			break;
		case 0x400E:			
			if (noise_enabled)
			{
				noise_sequencer.is_mode_flag_set = ((data & 0b10000000) != 0) ? true : false;
				switch (data & 0b00001111)
				{
				case 0x00: noise_sequencer.period = 4; break;
				case 0x01: noise_sequencer.period = 8; break;
				case 0x02: noise_sequencer.period = 16; break;
				case 0x03: noise_sequencer.period = 32; break;
				case 0x04: noise_sequencer.period = 64; break;
				case 0x05: noise_sequencer.period = 96; break;
				case 0x06: noise_sequencer.period = 128; break;
				case 0x07: noise_sequencer.period = 160; break;
				case 0x08: noise_sequencer.period = 202; break;
				case 0x09: noise_sequencer.period = 254; break;
				case 0x0A: noise_sequencer.period = 380; break;
				case 0x0B: noise_sequencer.period = 508; break;
				case 0x0C: noise_sequencer.period = 762; break;
				case 0x0D: noise_sequencer.period = 1016; break;
				case 0x0E: noise_sequencer.period = 2034; break;
				case 0x0F: noise_sequencer.period = 4068; break;
				}
			}
			break;
		case 0x400F:
			noise_envelope.start = true;
			noise_length_counter.length_counter = length_table[(data & 0xF8) >> 3];
			break;
		case 0x4010:
			dmc_irq_enabled = (data & 0x80) != 0;
			dmc_loop = (data & 0x40) != 0;
			int rate_index = data & 0x0F;
			dmc_rate = dmc_frequency_table[rate_index];
			dmc_sequencer.period = dmc_rate;
			break;
		case 0x4011:
			dmc_sequencer.output = data & 0x7F;
			break;
		case 0x4012:
			dmc_sample_address = 0xC000 | data << 6;
			dmc_memory_reader.address_counter = dmc_sample_address;
			break;
		case 0x4013:
			dmc_sample_length = (data * 16) + 1;
			dmc_memory_reader.bytes_remaining = dmc_sample_length;
			break;
		case 0x4015: // APU STATUS
			if ((data & 0x01) == 0)
			{
				pulse1_enabled = false;
				pulse1_length_counter.length_counter = 0;
			}
			else
			{
				pulse1_enabled = true;
			}
			
			if ((data & 0x02) == 0)
			{
				pulse2_enabled = false;
				pulse2_length_counter.length_counter = 0;
			}
			else
			{
				pulse2_enabled = true;
			}
			
			if ((data & 0x04) == 0)
			{
				triangle_enabled = false;
				triangle_length_counter.length_counter = 0;
			}
			else
			{
				triangle_enabled = true;
			}
			
			if ((data & 0x08) == 0)
			{
				noise_enabled = false;
				noise_length_counter.length_counter = 0;
			}
			else
			{
				noise_enabled = true;
			}
			
			if ((data & 0x10) == 0)
			{
				dmc_enabled = false;
				dmc_memory_reader.bytes_remaining = 0;
			}
			else
			{
				dmc_enabled = true;
				dmc_sequencer.play_sample = true;
				dmc_sequencer.bits_remaining = 0;
			}
			break;
		case 0x4017:
			sequencer_mode_is_4_step = (data & 0x80) == 0;
			interrupt_inhibit = (data & 0x40) == 0;
			if (sequencer_mode_is_4_step)
			{
			}
			else
			{
				should_clock_5_step_initial = true;
			}
			even_cpu_cycle_has_occurred = false;
			should_reset_frame_counter = true;
			break;
		}
	}
	
	public /*unsigned 8bit*/ int cpuRead(/*unsigned 16bit*/ int addr)
	{
		/*unsigned 8bit*/ int data = 0x00;
		
		switch (addr)
		{
		case 0x04000:
			break;
		case 0x4001:
			break;
		case 0x4002:
			break;
		case 0x4003:
			break;
		case 0x4004:
			break;
		case 0x4005:
			break;
		case 0x4006:
			break;
		case 0x4007:
			break;
		case 0x4008:
			break;
		case 0x400C:
			break;
		case 0x400E:
			break;
		case 0x4015: // APU STATUS
			data |= (pulse1_length_counter.length_counter > 0) ? 0x01 : 0x00;
			data |= (pulse2_length_counter.length_counter > 0) ? 0x02 : 0x00;		
			data |= (noise_length_counter.length_counter > 0) ? 0x04 : 0x00;
			break;
		case 0x400F:
			break;
		}

		return data;
	}
	
	public void clock()
	{
		// The NES's mechanism for playing notes works on a 4 or 5 step pattern (5 step actually being 4 steps with a pause)
		// per (visual) frame.
		// Each of the 4 steps can be called a "quarter frame" and every second step a "half frame". The variables below
		// keep track of where in the sequence we are. 
		boolean quarter_frame_clock = false;
		boolean half_frame_clock = false;
		
		// The frame counter steps at the speed of the CPU. Since this class is clocked at the speed of the PPU, we need
		// to only step every 3 cycles.
		pulse1_sweep.track();
		pulse2_sweep.track();
		
		// 4-Step Sequence Mode
		if (frame_counter == STEP1_SEQUENCER_STEPS)
		{
			quarter_frame_clock = true;
		}

		if (frame_counter == STEP2_SEQUENCER_STEPS)
		{
			quarter_frame_clock = true;
			half_frame_clock = true;
		}

		if (frame_counter == STEP3_SEQUENCER_STEPS)
		{
			quarter_frame_clock = true;
		}

		if (sequencer_mode_is_4_step)
		{
			if (frame_counter == STEP4_SEQUENCER_STEPS) 
			{
				quarter_frame_clock = true;
				half_frame_clock = true;
				frame_counter = 0;
			}
		}
		else
		{
			if (frame_counter == STEP5_SEQUENCER_STEPS) 
			{
				quarter_frame_clock = true;
				half_frame_clock = true;
				frame_counter = 0;
			}
		}
		
		// Update functional units

		// Quarter frame "beats" adjust the volume envelope and triangle linear length counter.
		if (quarter_frame_clock)
		{
			pulse1_envelope.clock();
			pulse2_envelope.clock();
			noise_envelope.clock();
			triangle_linear_counter.clock(triangle_enabled);
		}
		
		// Half frame "beats" adjust the note length and frequency sweepers.
		if (half_frame_clock)
		{
			pulse1_length_counter.clock(pulse1_enabled);
			pulse2_length_counter.clock(pulse2_enabled);
			triangle_length_counter.clock(triangle_enabled);
			noise_length_counter.clock(noise_enabled);
			pulse1_sweep.clock();
			pulse2_sweep.clock();
		}
		
		if (cpu_cycle % 2 == 0)
		{
			even_cpu_cycle_has_occurred = true;
		}
		else
		{	
			if (even_cpu_cycle_has_occurred && should_reset_frame_counter)
			{
				// After 2 or 3 CPU clock cycles*, the timer is reset.
				frame_counter = -1;
				should_reset_frame_counter = false;
				even_cpu_cycle_has_occurred = false;
				
				// Writing to $4017 with bit 7 set ($80) will immediately clock all of its controlled units at the 
				// beginning of the 5-step sequence
				if (should_clock_5_step_initial)
				{
					pulse1_envelope.clock();
					pulse2_envelope.clock();
					noise_envelope.clock();
					triangle_linear_counter.clock(triangle_enabled);
					
					pulse1_length_counter.clock(pulse1_enabled);
					pulse2_length_counter.clock(pulse2_enabled);
					triangle_length_counter.clock(triangle_enabled);
					noise_length_counter.clock(noise_enabled);
					pulse1_sweep.clock();
					pulse2_sweep.clock();
					should_clock_5_step_initial = false;
				}
			}
		}
		
		frame_counter++;
		cpu_cycle++;
		
		// The sequencers are clocked at half of the speed of the CPU.
		if (cpu_clock_counter % 2 == 0)
		{
			// Update Pulse1 Channel ================================
			pulse1_sequencer.clock(pulse1_enabled);
			if (pulse1_sequencer.period >= 8 && pulse1_length_counter.length_counter > 0 && !pulse1_sweep.mute)
			{
				pulse1_sample = 
					(pulse1_sequencer.output == 1) ? pulse1_envelope.output : 0;
			}
			else
			{
				if ((pulse1_sequencer.atStartOfWaveform()) || (pulse1_sample == 0))
				{
					pulse1_sample = 0;
				}
				else
				{
					pulse1_sample = 
							(pulse1_sequencer.output == 1) ? pulse1_envelope.output : 0;
				}
			}
			// Debugging code start
			synchronized(this)
			{
				pulse_1_debug.addSample((pulse1_sample - 7.5)/7.5);
			}
			// Debugging code end
			
			// Update Pulse2 Channel ================================
			pulse2_sequencer.clock(pulse2_enabled);
			if (pulse2_sequencer.period >= 8 && pulse2_length_counter.length_counter > 0 && !pulse2_sweep.mute)
			{
				pulse2_sample = 
						(pulse2_sequencer.output == 1) ? pulse2_envelope.output : 0;
			}
			else
			{
				if ((pulse2_sequencer.atStartOfWaveform()) || (pulse2_sample == 0))
				{
					pulse2_sample = 0;
				}
				else
				{
					pulse2_sample = 
							(pulse2_sequencer.output == 1) ? pulse2_envelope.output : 0;
				}
			}
			
			// Debugging code start
			synchronized(this)
			{
				pulse_2_debug.addSample((pulse2_sample - 7.5)/7.5);
			}
			// Debugging code end
			
			// Update Noise Channel ================================
			noise_sequencer.clock(noise_enabled);
			if (noise_length_counter.length_counter > 0)
			{
				noise_sample = 
						(noise_sequencer.output == 1) ? noise_envelope.output : 0;
			}
			else
			{
				noise_sample = 0;
			}
			// Debugging code start
			synchronized(this)
			{
				noise_debug.addSample((noise_sample - 7.5)/7.5);
			}
			// Debugging code end
			
			// Update Noise Channel ================================
			noise_sequencer.clock(noise_enabled);
			if ((noise_length_counter.length_counter > 0) && ((noise_sequencer.shift_register & 0x1) == 0))
			{
				noise_sample = 
						(noise_sequencer.output == 1) ? noise_envelope.output : 0;
			}
			else
			{
				noise_sample = 0;
			}
			// Debugging code start
			synchronized(this)
			{
				noise_debug.addSample((noise_sample - 7.5)/7.5);
			}
			// Debugging code end

			if (!pulse1_enabled) pulse1_sample = 0;
			if (!pulse2_enabled) pulse2_sample = 0;
			if (!noise_enabled) noise_sample = 0;
		}
		
		// The triangle sequencer and DMC are exceptions, they are clocked at the speed of the CPU.
		
		// Update Triangle Channel ================================
		if (triangle_length_counter.length_counter > 0 && triangle_linear_counter.length_counter > 0)
		{
			triangle_sequencer.clock(true);
			if (triangle_sequencer.period >= 2)
			{					
				triangle_sample = triangle_sequencer.output;
			}
		}
		// Debugging code start
		synchronized(this)
		{
			triangle_debug.addSample((triangle_sample - 7.5)/7.5);
		}
		// Debugging code end
		
		// Update DMC Channel ================================
		dmc_sequencer.clock();
		dmc_sample = dmc_sequencer.output;
		// Debugging code start
		synchronized(this)
		{
			dmc_debug.addSample((dmc_sample - 63.5)/63.5);
		}
		// Debugging code end

		cpu_clock_counter++;
	}
	
	public void reset()
	{
		cpu_clock_counter = 0;
		frame_counter = 0;
		cpu_cycle = 0;
		should_reset_frame_counter = false;
		should_clock_5_step_initial = false;
		even_cpu_cycle_has_occurred = false;
		interrupt_inhibit = false;
		pulse1_enabled = false;
		pulse2_enabled = false;
		triangle_enabled = false;
		noise_enabled = false;
		pulse1_sample = 0;
		pulse2_sample = 0;
		triangle_sample = 0;
		noise_sample = 0;
		sequencer_mode_is_4_step = true;

		pulse1_sequencer = new PulseSequencer();
		pulse1_envelope = new Envelope();
		pulse1_length_counter = new LengthCounter();
		pulse1_sweep = new Sweeper(pulse1_sequencer, 1);
		
		pulse2_sequencer = new PulseSequencer();
		pulse2_envelope = new Envelope();
		pulse2_length_counter = new LengthCounter();
		pulse2_sweep = new Sweeper(pulse2_sequencer, 0);
		
		triangle_sequencer = new TriangleSequencer();
		triangle_length_counter = new LengthCounter();
		triangle_linear_counter = new LinearCounter();
		
		noise_sequencer = new NoiseSequencer();
		noise_envelope = new Envelope();
		noise_length_counter = new LengthCounter();
		noise_sequencer.shift_register = 1;
		
		dmc_memory_reader = new DMCMemoryReader(bus);
		dmc_sequencer = new DMCSequencer(dmc_memory_reader);
	}
	
	private static class Sequencer
	{
		// The value currently in the shift register.
		//
		// For pulse wave sequencers:
		// The waveform that the sequencer will output, represented as a 8bit value, depending on the duty cycle.
		// Can be one of the following which will be shifted left on every clock:
		//   Duty cycle=12.5%: 0 1 0 0 0 0 0 0
		//   Duty cycle=25%  : 0 1 1 0 0 0 0 0
		//   Duty cycle=50%  : 0 1 1 1 1 0 0 0
		//   Duty cycle=-25% : 1 0 0 1 1 1 1 1
		//
		// For noise sequencers:
		// The pseudo-random string of 1s and 0s that generates the noise sound which will change on every clock.
		protected int shift_register = 0x00;
		
		// The timer value that times the frequency of the waveform.
		//   Initially, the timer is set to 0
		//   It then counts down
		//   If it reaches -1, a bit is extracted from the waveform and the timer is set to the current period
		//   The above 2 steps are repeated
		protected /*unsigned 16bit*/ int timer = 0x00;
		
		// The period of the sound wave i.e. the time taken to complete one full sine wave. This is related to the
		// frequency of the sound wave as follows: 
		// 		sound_frequency = CPU_clock_frequency / (16 * (period + 1))
		// Since normally frequency = 1 / period, this means the scaling factor to convert periods to frequency is
		// CPU_clock_frequency / 16.
		// The lowest period that can be set is 8, anything below 8 silences the channel.
		// This means the highest sound frequency that can thus be achieved (for NTSC) is 
		// 		1.789773MHz / (16 * (8 + 1)) = 12,42897KHz
		// Since the period is a 11 digit number, the lowest frequency that can be achieved is:
		//      1.789773MHz / (16 * (2047 + 1)) = 54,61953Hz
		protected /*unsigned 16bit*/ int period = 0;
		
		// The sample value i.e. one bit of the waveform (either a 1 or a 0) that is currently being output by the sequencer
		// for pulse and noise channels.
		// OR
		// The current 4-bit level of the triangle waveform between 0 and 15. 
		protected /*unsigned 8bit*/ int output = 0x00;
		
		// Outputs the next sample from the shift register and rotate the shift register one bit to the left or right,
		// depending on the type of sequencer.
		public int outputSampleAndRotateShiftRegister()
		{
			return 0;
		}
		
		// Clock the sequencer.
		public /*unsigned 8bit*/ int clock(boolean enable)
		{
			if (enable)
			{
				timer--;
				if (timer == -1)
				{
					timer = period;
					output = outputSampleAndRotateShiftRegister();
				}
			}
			return output;
		}
	}
	
	private static class PulseSequencer extends Sequencer
	{
		// Which bit of the 8 bit waveform the sequencer is currently at. Waveforms are only changed when they have
		// been played completely, hence the need to track where in the waveform the sequencer currently is.
		private int waveform_step = 0;
		
		// The new waveform that has been written by the CPU. The waveform is first stored in a temporary variable and
		// then changed when the previous waveform has completed. If this is not done, the waveform will be changed whilst
		// it is being played and it will sound horrible.
		public int shift_register_load_value = 0x00;
		
		// Resets the phase of the waveform.
		public void resetPhase()
		{
			timer = 0;
			waveform_step = 0;
			shift_register = shift_register_load_value;
		}
		
		// Returns whether the pulse wave is at the start of the waveform or not.
		public boolean atStartOfWaveform()
		{
			return waveform_step == 0;
		}
		
		// Outputs the most significant bit and rotate the shift register 1 bit to the left.
		public int outputSampleAndRotateShiftRegister()
		{
			int return_value = (shift_register >> 7) & 0x1;
			shift_register = (shift_register << 1) | return_value;
			return return_value;
		}
		
		// Clock the sequencer. Note this method is slightly different to the generic one because we don't want to
		// change the shift register if the waveform is still playing, so use a temporary variable first and then
		// change it when the waveform has finished playing.
		public /*unsigned 8bit*/ int clock(boolean enable)
		{
			if (enable)
			{
				timer--;
				if (timer == -1)
				{
					timer = period;
					if (period != 0)
					{
						if (atStartOfWaveform())
						{
							// Reload the shift register waveform value only when the previous one has finished playing.
							shift_register = shift_register_load_value;
						}
						output = outputSampleAndRotateShiftRegister();
						waveform_step++;
						waveform_step %= 8;
					}
				}
			}
			return output;
		}
	}
	
	private static class NoiseSequencer extends Sequencer
	{
		protected boolean is_mode_flag_set = false;
		
		public int outputSampleAndRotateShiftRegister()
		{
			//If mode=0, then 32,767-bit long number sequences will be produced (32K 
			//mode), otherwise 93-bit long number sequences will be produced (93-bit 
			//mode).

			//The following diagram shows where the XOR taps are taken off the shift 
			//register to produce the 1-bit pseudo-random number sequences for each mode.

			//mode	    <-----
			//----	EDCBA9876543210
			//32K	**
			//93-bit	*     *
			int return_value;
			if (is_mode_flag_set)
			{
				return_value = ((shift_register ^ (shift_register << 6)) >> 14) & 0x1;
			}
			else
			{
				return_value = ((shift_register ^ (shift_register << 1)) >> 14) & 0x1;
			}
			
			// The current result of the XOR will be transferred into bit position 0 of the 
			// SR, upon the next shift cycle.
			shift_register <<= 1;
			shift_register &= 0x7FFE;
			shift_register |= return_value;
			
			// The 1-bit random number output is taken from 
			// pin E, is inverted, then is sent to the volume/envelope decay hardware for 
			// the noise channel.
			return_value = (~return_value) & 0x1;
			
			return return_value;
		}
	}
	
	private static class TriangleSequencer extends Sequencer
	{
		private final int[] sequence =
			{15, 14, 13, 12, 11, 10, 9, 8, 7, 6,  5,  4,  3,  2,  1,  0,
			 0,   1,  2,  3,  4,  5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
			};
		private int sequence_step = 0;
		
		// Steps to the next level in the triangle wave and wraps around when at the end.
		public int outputSampleAndRotateShiftRegister()
		{
			int return_value = sequence[sequence_step];
			sequence_step++;
			sequence_step %= 32;
			return return_value;
		}
	}
	
	private static class DMCSequencer extends Sequencer
	{
		private int bits_remaining = 8;
		private boolean silence = true;
		private DMCMemoryReader dmc_memory_reader;
		public boolean play_sample = false;
		private boolean dmc_inverted = false;
		
		public DMCSequencer(DMCMemoryReader dmc_memory_reader)
		{
			this.dmc_memory_reader = dmc_memory_reader;
		}
		
		// Steps to the next level in the triangle wave and wraps around when at the end.
		public int outputSampleAndRotateShiftRegister()
		{
			if (!dmc_inverted)
			{
				int return_value = shift_register & 0x1;
				shift_register >>= 1;
				return return_value;
			}
			else
			{
				int return_value = (shift_register & 0b10000000) >> 7;
				shift_register <<= 1;
				return return_value;
			}
		}
		
		// Clock the sequencer.
		public /*unsigned 8bit*/ int clock()
		{
			timer--;
			if (timer == -1)
			{
				timer = period;
				if (play_sample)
				{
					if (bits_remaining == 0)
					{
						bits_remaining = 8;
						
						if (dmc_memory_reader.bytes_remaining == 0)
						{
							play_sample = false;
						}
						else
						{
							shift_register = dmc_memory_reader.getSampleBuffer();
							if (shift_register == 0)
							{
								silence = true;
							}
							else
							{
								silence = false;
							}
						}
					}
					int delta = outputSampleAndRotateShiftRegister();
					if (delta == 1)
					{
						if (output <= 125)
						{
							output += 2;
						}
					}
					else
					{
						if (output >= 2)
						{
							output -= 2;
						}
					}
					bits_remaining--;
				}
			}
			return output;
		}
	}
	
	private static class DMCMemoryReader
	{
		private int address_counter;
		private int bytes_remaining;
		
		private NESBus bus;
		
		public DMCMemoryReader(NESBus bus)
		{
			this.bus = bus;
		}
		
		public int getSampleBuffer()
		{
			if (bytes_remaining > 0)
			{
				int return_value = bus.cpuRead(address_counter);
				address_counter++;
				if (address_counter > 0xFFFF)
				{
					address_counter = 0x8000;
				}
				bytes_remaining--;
				return return_value;
			}
			else
			{
				return 0;
			}
		}
	}
	
	private static class Envelope
	{
		private boolean start = false;
		private boolean constant_volume = false;
		private /*unsigned 16bit*/ int divider_count = 0;
		private /*unsigned 16bit*/ int volume = 0;
		private /*unsigned 16bit*/ int period = 0;
		private /*unsigned 16bit*/ int output = 0;
		private /*unsigned 16bit*/ int decay_level_counter = 0;
		private boolean loop = false;
		
		private void clock()
		{
			// When clocked by the frame counter, one of two actions occurs
			if (!start)
			{
				// If the start flag is clear, the divider is clocked
				if (divider_count == 0)
				{
					// When the divider is clocked while at 0, it is loaded with V (i.e. the period) and 
					// clocks the decay level counter.
					divider_count = period;
					
					// Then one of two actions occurs:
					if (decay_level_counter == 0)
					{
						// Otherwise if the loop flag is set, the decay level counter is loaded with 15
						if (loop)
						{
							decay_level_counter = 15;
						}
					}
					else
					{
						// If the counter is non-zero, it is decremented
						decay_level_counter--;
					}
				}
				else
					divider_count--;
			}
			else
			{
				// Otherwise the start flag is cleared, the decay level counter is loaded with 15, 
				// and the divider's period is immediately reloaded
				start = false;
				decay_level_counter = 15;
				divider_count = period;
			}

			// The envelope unit's volume output depends on the constant volume flag
			if (constant_volume)
			{
				// If set, the envelope parameter directly sets the volume
				output = volume;
				
				// The constant volume flag has no effect besides selecting the volume source; the decay level 
				// will still be updated when constant volume is selected.
			}
			else
			{
				// Otherwise the decay level is the current volume
				output = decay_level_counter;
			}
		}
	};
	
	private static class LengthCounter
	{
		private /*unsigned 8bit*/ int length_counter = 0x00;
		private boolean halt = false;
		
		private /*unsigned 8bit*/ int clock(boolean enable)
		{
			if (!enable)
				length_counter = 0;
			else
				if (length_counter > 0 && !halt)
					length_counter--;
			return length_counter;
		}
	};
	
	private static class LinearCounter
	{
		private /*unsigned 8bit*/ int length_counter = 0x00;
		private /*unsigned 8bit*/ int reload_value = 0x00;
		private boolean reload = false;
		private boolean control = false;
		
		private /*unsigned 8bit*/ int clock(boolean enable)
		{
			// If the linear counter reload flag is set, the linear counter is reloaded with the counter reload value
			if (reload)
			{
				length_counter = reload_value;
			}
			else
			{
				if (!enable)
				{
					length_counter = 0;
				}
				else
				{
					// Otherwise if the linear counter is non-zero, it is decremented.
					if (length_counter > 0)
						length_counter--;
				}
			}
			
			// If the control flag is clear, the linear counter reload flag is cleared.
			if (!control)
			{
				reload = false;
			}
			
			return length_counter;
		}
	};
	
	private static class Sweeper
	{
		private boolean enabled = false;
		private boolean negate = false;
		private boolean reload = false;
		private /*unsigned 8bit*/ int shift = 0x00;
		private /*unsigned 8bit*/ int divider_counter = 0x00;
		private /*unsigned 8bit*/ int divider_period = 0x00;
		private /*unsigned 16bit*/ int change = 0;
		private boolean mute = false;
		
		// The sequencer of the channel to track. Since the sweeper uses the currently set period of a channel
		// (for example pulse channel 1) as the target to sweep towards or away from, it needs a reference to
		// the current period i.e. it needs a reference to the sequencer.
		private Sequencer sequencer_to_track;
		
		// Which pulse channel this sweeper operates on. There is a slight calculation difference between the 2 
		// pulse_channels hence the need for this variable.
		// 		1 - Pulse channel 1
		//		0 - Pulse channel 2
		private int pulse_channel;
		
		private Sweeper(Sequencer sequencer_to_track, int pulse_channel)
		{
			this.sequencer_to_track = sequencer_to_track;
			this.pulse_channel = pulse_channel;
		}

		private /*unsigned 16bit*/ void track()
		{
			// The sweep unit continuously calculates each channel's target period in this way:
			if (enabled)
			{
				// A barrel shifter shifts the channel's 11-bit raw timer period right by the shift count, producing the 
				// change amount.
				change = sequencer_to_track.period >> shift;
				mute = (sequencer_to_track.period < 8) || (sequencer_to_track.period > 0x7FF);
			}
		}

		private void clock()
		{
			if (divider_counter == 0 && enabled && !mute)
			{
				if (sequencer_to_track.period >= 8 && change < 0x07FF)
				{
					// If the negate flag is true, the change amount is made negative.
					if (negate)
					{
						change = -change;
					}
					
					// The target period is the sum of the current period and the change amount.
					// The two pulse channels have their adders' carry inputs wired differently, which produces different results when each channel's change amount is made negative:
					// Pulse 1 adds the ones' complement (-c - 1). e.g. making 20 negative produces a change amount of -21.
					// Pulse 2 adds the two's complement (-c). e.g. making 20 negative produces a change amount of -20.
					sequencer_to_track.period += change - pulse_channel;

					sequencer_to_track.period = sequencer_to_track.period & 0xFFFF;
				}
			}

			//if (enabled)
			{
				if (divider_counter == 0 || reload)
				{
					divider_counter = divider_period;
					reload = false;
				}
				else
					divider_counter--;

				mute = (sequencer_to_track.period < 8) || (sequencer_to_track.period > 0x7FF);
			}
		}
	};
	
	// Debugging methods
	public DebugSamples getPulse1Debug()
	{
		return pulse_1_debug;
	}
	
	public DebugSamples getPulse2Debug()
	{
		return pulse_2_debug;
	}
	
	public DebugSamples getTriangleDebug()
	{
		return triangle_debug;
	}
	
	public DebugSamples getNoiseDebug()
	{
		return noise_debug;
	}
	
	public DebugSamples getDMCDebug()
	{
		return dmc_debug;
	}
	
	public DebugSamples getOverallDebug()
	{
		return overall_debug;
	}
	
	public int getDebugSamplesToKeep()
	{
		return DEBUG_SAMPLES_TO_KEEP;
	}
	
	public void setPulse1Muted(boolean is_muted)
	{
		pulse_1_muted = is_muted;
	}
	
	public void setPulse2Muted(boolean is_muted)
	{
		pulse_2_muted = is_muted;
	}
	
	public void setTriangleMuted(boolean is_muted)
	{
		triangle_muted = is_muted;
	}
	
	public void setNoiseMuted(boolean is_muted)
	{
		noise_muted = is_muted;
	}
	
	public void setDMCMuted(boolean is_muted)
	{
		dmc_muted = is_muted;
	}
	
	public void setInverted(boolean is_inverted)
	{
		dmc_sequencer.dmc_inverted = is_inverted;
	}
	
	public boolean pulse1GetVibrato()
	{
		return pulse_1_vibrato;
	}
	
	public boolean pulse2GetVibrato()
	{
		return pulse_2_vibrato;
	}
	
	public static class DebugSamples
	{
		private int total_sample_count = 0;
		public int last_debug_sample_position;
		public double[] debug_samples = new double[DEBUG_SAMPLES_TO_KEEP];
		
		public void addSample(double sample)
		{
			if (total_sample_count % 30 == 0)
			{
				debug_samples[last_debug_sample_position] = sample;
				incLastDebugSamplePosition();
			}
			total_sample_count++;
		}
		
		private void incLastDebugSamplePosition()
		{
			last_debug_sample_position++;
			last_debug_sample_position %= DEBUG_SAMPLES_TO_KEEP;
		}
		
		public DebugSamples getCopy()
		{
			DebugSamples return_value = new DebugSamples();
			for (int n=0; n < DEBUG_SAMPLES_TO_KEEP; n++)
			{
				return_value.debug_samples[n] = debug_samples[n];
			}
			return_value.last_debug_sample_position = last_debug_sample_position;
			return return_value;
		}
	}

}
