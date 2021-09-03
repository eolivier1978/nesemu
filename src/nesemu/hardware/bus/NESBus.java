package nesemu.hardware.bus;

import nesemu.hardware.audio.RP2A03;
import nesemu.hardware.cartridge.ACartridge;
import nesemu.hardware.cpu.MOS6502;
import nesemu.hardware.video.RP2C02;

public class NESBus
{
	// Devices on the bus
	public MOS6502 cpu;
	
	// The 2C02 Picture Processing Unit
	public RP2C02 ppu;
	
	// The 2A03 Audio Processing Unit
	public RP2A03 apu;
	
	// The Cartridge or "GamePak"
	public ACartridge cartridge;
	
	// 2KB of on-board RAM
	private /*unsigned 8bit*/ int[] wram = new /*unsigned 8bit*/ int[2048];
	
	// The controllers i.e. the gamepads, light gun, etc. Each controller is fully described by 8 bits. 
	public int[] controller;
	
	// The current state of each controller.
	private int[] controller_state;
	
	// A simple form of Direct Memory Access is used to quickly
	// transfer data from CPU bus memory into the OAM memory. It would
	// take too long to sensibly do this manually using a CPU loop, so
	// the program prepares a page of memory with the sprite info required
	// for the next frame and initiates a DMA transfer. This suspends the
	// CPU momentarily while the PPU gets sent data at PPU clock speeds.
	// Note here that dma_page and dma_addr form a 16-bit address in 
	// the CPU bus address space.
	public /*unsigned 8bit*/  int dma_page;
	public /*unsigned 8bit*/  int dma_addr;
	public /*unsigned 8bit*/  int dma_data;
	
	// DMA transfers need to be timed accurately. In principle it takes
	// 512 cycles to read and write the 256 bytes of the OAM memory, a
	// read followed by a write. However, the CPU needs to be on an "even"
	// clock cycle, so a dummy cycle of idleness may be required.
	public boolean dma_dummy;
	// A flag to indicate that a DMA transfer is happening.
	public boolean dma_transfer;
	
	// A count of how many clocks have passed
	private long system_clock_counter = 0;
	
	// Variables tracking if the NES is on or off.
	public boolean is_powered_on = false;
	public boolean is_starting_up = false;
	
	// 236.25 MHz ÷ 11 per NTSC definition
	public static final double MASTER_NTSC_FREQUENCY = 236.25 / 11.0; //mHz 
	
	// Constructor
	public NESBus()
	{
		reset();
	}
	
	// Writing to and reading from the bus.
	public void cpuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		if (cartridge.cpuWrite(addr, data))
		{
			// The cartridge "sees all" and has the ability to veto
			// the propagation of the bus transaction if it requires.
			// This allows the cartridge to map any address to some
			// other data, including the ability to divert transactions
			// with other physical devices.
		}
		else if (addr >= 0x0000 && addr <= 0x1FFF)
		{
			// System RAM address range. The range covers 8KB, though
			// there is only 2KB available. That 2KB is "mirrored"
			// through this address range. Using bitwise AND to mask
			// the bottom 11 bits is the same as addr % 2048.
			wram[addr & 0x07FF] = data;
		}
		else if (addr >= 0x2000 && addr <= 0x3FFF)
		{
			// PPU address range. The PPU only has 8 primary registers
			// and these are repeated throughout this range. We can
			// use bitwise AND operation to mask the bottom 3 bits, 
			// which is the equivalent of addr % 8.
			ppu.cpuWrite(addr & 0x0007, data);
		}
		else if ((addr >= 0x4000 && addr <= 0x4013) || (addr == 0x4015) || (addr == 0x4017))
		{
			// APU address range.
			apu.cpuWrite(addr, data);
		}
		else if (addr == 0x4014)
		{
			// DMA register. It's annoying that it's in the middle of the APU range.
			dma_page = data;
			dma_addr = 0x00;
			dma_transfer = true;
		}
		else if (addr == 0x4016)
		{
			// Controllers address range.
			if ((data & 0x01) == 1)
			{
				controller_state[0] = controller[0];
				controller_state[1] = controller[1];
			}
		}
	}
	
	public /*unsigned 8bit*/ int cpuRead(/*unsigned 16bit*/ int addr)
	{
		return cpuRead(addr, false);
	}
	
	public /*unsigned 8bit*/ int cpuRead(/*unsigned 16bit*/ int addr, boolean bReadOnly)
	{
		/*unsigned 8bit*/ int[] data = new int[1];
		if (cartridge.cpuRead(addr, data))
		{
			// Cartridge Address Range
		}
		else if (addr >= 0x0000 && addr <= 0x1FFF)
		{
			// System RAM address range, mirrored every 2048.
			data[0] = wram[addr & 0x07FF];
		}
		else if (addr >= 0x2000 && addr <= 0x3FFF)
		{
			// PPU address range, mirrored every 8.
			data[0] = ppu.cpuRead(addr & 0x0007, bReadOnly);
		}
		else if ((addr >= 0x4000 && addr <= 0x4013) || (addr == 0x4015))
		{
			// APU address range. Note that when reading address 4017 reads controller 2 and does not read from the APU.
			data[0] = apu.cpuRead(addr);
		}
		else if (addr == 0x4016)
		{
			// Controller 1 address range.
			data[0] = (controller_state[0] & 0x80) > 0 ? 1 : 0;
			controller_state[0] <<= 1;
		}
		else if (addr == 0x4017)
		{
			// Controller 2 address range.
			data[0] = (controller_state[1] & 0x80) > 0 ? 1 : 0;
			controller_state[1] <<= 1;
		}
		return data[0];
	}
	
	public void insertCartridge(ACartridge cartridge)
	{
		// Connects cartridge to both main bus and CPU bus.
		this.cartridge = cartridge;
		ppu.ConnectCartridge(cartridge);
	}
	
	public void reset()
	{
		// Initialize the RAM to all zeroes
		for (int n=0; n < 2048; n++)
		{
			wram[n] = 0;
		}
		
		cpu = new MOS6502();
		ppu = new RP2C02();
		apu = new RP2A03();
		cpu.ConnectBus(this);
		apu.connectToBus(this);
		
		controller = new int[2];
		controller_state = new int[2];
		
		dma_page = 0x00;
		dma_addr = 0x00;
		dma_data = 0x00;
		
		dma_dummy = true;
		
		dma_transfer = false;
		
		if (cartridge != null)
		{
			cartridge.reset();
			cpu.reset();
			ppu.reset();
			apu.reset();
			ppu.ConnectCartridge(cartridge);
		}
		
		system_clock_counter = 0;
	}
	
	public void powerOn()
	{
		is_powered_on = true;
	}
	
	public void powerOff()
	{
		is_powered_on = false;
	}
	
	public void clock()
	{
		// Clock the bus in the same way as the clock chip would have done.
		// The running frequency is controlled by whatever calls this function.
		// We "divide" the clock as necessary and call the peripheral devices clock() 
		// function at the correct times.

		// The fastest clock frequency the digital system cares about is the PPU 
		// clock. So the PPU is clocked each time this function is called.
		ppu.clock();

		// The CPU runs 3 times slower than the PPU so we only call its
		// clock() function every 3 times this function is called. We
		// have a global counter to keep track of this.
		if (system_clock_counter % 3 == 0)
		{
			// Even though the APU runs at half of the CPU clock speed clock it at 
			// the CPU clock rate because the APU can change state on CPU clock
			// cycles, so we need to be able to cater for that. The APU and CPU
			// are on the same chip so another reason to clock it at the same speed.
			apu.clock();
			
			// Is the system performing a DMA transfer from CPU memory to 
			// OAM memory on PPU?
			if (dma_transfer)
			{
				// Yes, we need to wait until the next even CPU clock cycle
				// before it starts.
				if (dma_dummy)
				{
					// Wait here until 1 or 2 cycles have elapsed.
					if (system_clock_counter % 2 == 1)
					{
						// Finally allow DMA to start.
						dma_dummy = false;
					}
				}
				else
				{
					// DMA can take place.
					if (system_clock_counter % 2 == 0)
					{
						// On even clock cycles, read from CPU bus.
						dma_data = cpuRead(dma_page << 8 | dma_addr);
					}
					else
					{
						// On odd clock cycles, write to PPU OAM.
						ppu.pOAM[dma_addr] = dma_data;
						// Increment the low byte of the address.
						dma_addr++;
						
						// If this wraps around, we know that 256
						// bytes have been written, so end the DMA
						// transfer, and proceed as normal.
						if (dma_addr > 0xFF)
						{
							ppu.populateOAM();
							dma_transfer = false;
							dma_dummy = true;
							dma_addr = 0;
						}
					}
				}
			}
			else
			{
				cpu.clock();
			}
		}
		
		// The PPU is capable of emitting an interrupt to indicate the
		// vertical blanking period has been entered. If it has, we need
		// to send that IRQ to the CPU.
		if (ppu.nmi)
		{
			ppu.nmi = false;
			cpu.nmi();
		}

		system_clock_counter++;
	}
	
	// Debug method
	// Run one CPU instruction.
	public void runCPUInstruction()
	{
		// Clock enough times to execute a whole CPU instruction.
		do
		{
			cpu.clock();
		} 
		while (!cpu.complete());
		
		// CPU clock runs slower than system clock, so it may be
		// complete for additional system clock cycles. Drain
		// those out.
		do
		{
			clock();
		}
		while (cpu.complete());
	}
}
