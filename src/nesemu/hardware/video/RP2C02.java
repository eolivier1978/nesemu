package nesemu.hardware.video;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import nesemu.debugger.PPUGUIDebugger.Sprite;
import nesemu.hardware.bus.NESBus;
import nesemu.hardware.cartridge.ACartridge;
import nesemu.hardware.mapper.AMapper.MIRROR;

public class RP2C02
{
	private ACartridge cartridge;
	
	// Two 1KB name tables
	public /*unsigned 8bit*/ int[][] name_table;
	
	// Two 4KB pattern tables
	public /*unsigned 8bit*/ int[][] pattern_table;
	
	// Palette
	public /*unsigned 8bit*/ int[] palette;
	
	// The NES palette.
	private static final int[] nes_palette = new int[64];
	
	// Current location of drawing on the screen.
	private /*unsigned 16bit*/ int cycle;
	private /*unsigned 16bit*/ int scanline;
	
	// PPU's registers
	private /*unsigned 8bit*/  int status; // Status register
	private /*unsigned 8bit*/  int mask; // Mask register
	private /*unsigned 8bit*/  int control; // PPUCtrl register
	private LOOPY_REGISTER vram_addr;
	private LOOPY_REGISTER tram_addr;
	
	// Variables storing information about the next background tile to be drawn. 
	private /*unsigned 8bit*/  int bg_next_tile_id;
	private /*unsigned 8bit*/  int bg_next_tile_attrib;
	private /*unsigned 8bit*/  int bg_next_tile_lsb;
	private /*unsigned 8bit*/  int bg_next_tile_msb;
	
	// Variables storing the current background pattern to be drawn.
	private /*unsigned 16bit*/  int bg_shifter_pattern_lo;
	private /*unsigned 16bit*/  int bg_shifter_pattern_hi;
	private /*unsigned 16bit*/  int bg_shifter_attrib_lo;
	private /*unsigned 16bit*/  int bg_shifter_attrib_hi;
	
	// The current fine_x position.
	private /*unsigned 8bit*/  int fine_x;
	
	// Non maskable interrupt.
	public boolean nmi;
	
	// Since a write to a PPU register sometimes takes 2 cycles, variables are needed to store temporary values.
	private /*unsigned 8bit*/ int address_latch = 0x00;
	private /*unsigned 8bit*/ int ppu_data_buffer = 0x00;
	
	// Variables keeping information about the offscreen frames that have been drawn. 
	private static final int FRAMES_TO_KEEP = 4;
	private int[] frame_being_drawn_screen_data;
	private Frame[] frames = new Frame[FRAMES_TO_KEEP];
	private int current_frame_index;
	private int total_frames_drawn;
	public boolean frame_complete = false;
	
	private Sprite[] pattern_table_debug = { new Sprite(128, 128), new Sprite(128, 128) };
	
	// Variables storing the OAM (Object Attribute Memory)
	private /*unsigned 8bit*/ int oam_addr;
	private ObjectAttributeEntry[] OAM;
	public /*unsigned 8bit*/ int[] pOAM;
	
	// Variables storing the sprite information.
	private ObjectAttributeEntry[] spriteScanline;
	private /*unsigned 8bit*/ int sprite_count;
	
	// Variables storing the current sprite to draw.
	private /*unsigned 8bit*/ int[] sprite_shifter_pattern_lo;
	private /*unsigned 8bit*/ int[] sprite_shifter_pattern_hi;
	
	// Sprite Zero Collision Flags
	private boolean sprite_zero_hit_possible;
	private boolean sprite_zero_being_rendered;
	
	// State variables used during rendering.
	private int current_pixel;
	private boolean is_rendering_background;
	private boolean is_rendering_background_or_sprites;
	private boolean is_rendering_sprites;
	private boolean is_greyscale;
	private int grey_scale_value;
	
	// Variables used for optimization.
	private /*unsigned 8bit*/ int[] data_by_ref = new int[1];
	private int pattern_background_shifted_left_by_12;
	private int pattern_sprite_shifted_left_by_12;
	private int increment_mode_value;
	private int sprite_size;
	private static final int vblank_soverflow_szerohit_off = 
		STATUS2C02.VERTICAL_BLANK | STATUS2C02.SPRITE_OVERFLOW | STATUS2C02.SPRITE_ZERO_HIT;
	
	// The NTSC PPU operates at a quarter of the master clock speed.
	public static final double NTSC_FREQUENCY = NESBus.MASTER_NTSC_FREQUENCY / 4.0;
	
	public static final int PPU_FRAME_CLOCKS = 89342;
	
	public static final int NES_GRAY = 5526612;
	
	public RP2C02()
	{
		reset();
	}
	
	private int getColor(int red, int green, int blue)
	{
		int color = 0;
		color = color | ((red   & 0xff) << 16);
		color = color | ((green & 0xff) << 8);
		color = color | ((blue  & 0xff) << 0);
		
		return color;
	}
	
	private void initPalette()
	{
		nes_palette[0x00] = getColor(84, 84, 84);
		nes_palette[0x01] = getColor(0, 30, 116);
		nes_palette[0x02] = getColor(8, 16, 144);
		nes_palette[0x03] = getColor(48, 0, 136);
		nes_palette[0x04] = getColor(68, 0, 100);
		nes_palette[0x05] = getColor(92, 0, 48);
		nes_palette[0x06] = getColor(84, 4, 0);
		nes_palette[0x07] = getColor(60, 24, 0);
		nes_palette[0x08] = getColor(32, 42, 0);
		nes_palette[0x09] = getColor(8, 58, 0);
		nes_palette[0x0A] = getColor(0, 64, 0);
		nes_palette[0x0B] = getColor(0, 60, 0);
		nes_palette[0x0C] = getColor(0, 50, 60);
		nes_palette[0x0D] = getColor(0, 0, 0);
		nes_palette[0x0E] = getColor(0, 0, 0);
		nes_palette[0x0F] = getColor(0, 0, 0);

		nes_palette[0x10] = getColor(152, 150, 152);
		nes_palette[0x11] = getColor(8, 76, 196);
		nes_palette[0x12] = getColor(48, 50, 236);
		nes_palette[0x13] = getColor(92, 30, 228);
		nes_palette[0x14] = getColor(136, 20, 176);
		nes_palette[0x15] = getColor(160, 20, 100);
		nes_palette[0x16] = getColor(152, 34, 32);
		nes_palette[0x17] = getColor(120, 60, 0);
		nes_palette[0x18] = getColor(84, 90, 0);
		nes_palette[0x19] = getColor(40, 114, 0);
		nes_palette[0x1A] = getColor(8, 124, 0);
		nes_palette[0x1B] = getColor(0, 118, 40);
		nes_palette[0x1C] = getColor(0, 102, 120);
		nes_palette[0x1D] = getColor(0, 0, 0);
		nes_palette[0x1E] = getColor(0, 0, 0);
		nes_palette[0x1F] = getColor(0, 0, 0);

		nes_palette[0x20] = getColor(236, 238, 236);
		nes_palette[0x21] = getColor(76, 154, 236);
		nes_palette[0x22] = getColor(120, 124, 236);
		nes_palette[0x23] = getColor(176, 98, 236);
		nes_palette[0x24] = getColor(228, 84, 236);
		nes_palette[0x25] = getColor(236, 88, 180);
		nes_palette[0x26] = getColor(236, 106, 100);
		nes_palette[0x27] = getColor(212, 136, 32);
		nes_palette[0x28] = getColor(160, 170, 0);
		nes_palette[0x29] = getColor(116, 196, 0);
		nes_palette[0x2A] = getColor(76, 208, 32);
		nes_palette[0x2B] = getColor(56, 204, 108);
		nes_palette[0x2C] = getColor(56, 180, 204);
		nes_palette[0x2D] = getColor(60, 60, 60);
		nes_palette[0x2E] = getColor(0, 0, 0);
		nes_palette[0x2F] = getColor(0, 0, 0);

		nes_palette[0x30] = getColor(236, 238, 236);
		nes_palette[0x31] = getColor(168, 204, 236);
		nes_palette[0x32] = getColor(188, 188, 236);
		nes_palette[0x33] = getColor(212, 178, 236);
		nes_palette[0x34] = getColor(236, 174, 236);
		nes_palette[0x35] = getColor(236, 174, 212);
		nes_palette[0x36] = getColor(236, 180, 176);
		nes_palette[0x37] = getColor(228, 196, 144);
		nes_palette[0x38] = getColor(204, 210, 120);
		nes_palette[0x39] = getColor(180, 222, 120);
		nes_palette[0x3A] = getColor(168, 226, 144);
		nes_palette[0x3B] = getColor(152, 226, 180);
		nes_palette[0x3C] = getColor(160, 214, 228);
		nes_palette[0x3D] = getColor(160, 162, 160);
		nes_palette[0x3E] = getColor(0, 0, 0);
		nes_palette[0x3F] = getColor(0, 0, 0);
	}
	
	// Communication with Main Bus
	public void cpuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		switch (addr)
		{
		case 0x0000: // Control
			control = data;
			tram_addr.NAMETABLE_X = GetPPUCtrlFlag(PPUCTRL2C02.NAMETABLE_X) != 0 ? 1 : 0;
			tram_addr.NAMETABLE_Y = GetPPUCtrlFlag(PPUCTRL2C02.NAMETABLE_Y) != 0 ? 1 : 0;
			pattern_background_shifted_left_by_12 = GetPatternBackGroundIfSet();
			pattern_sprite_shifted_left_by_12 = GetPatternSpriteIfSet();
			increment_mode_value = GetPPUCtrlFlag(PPUCTRL2C02.INCREMENT_MODE) != 0 ? 32 : 1;
			sprite_size = (GetPPUCtrlFlag(PPUCTRL2C02.SPRITE_SIZE) != 0 ? 16 : 8);
			break;
		case 0x0001: // Mask
			mask = data;
			is_rendering_background = GetMaskFlag(MASK2C02.RENDER_BACKGROUND) != 0;
			is_rendering_sprites = GetMaskFlag(MASK2C02.RENDER_SPRITES) != 0;
			is_rendering_background_or_sprites = is_rendering_background | is_rendering_sprites;
			is_greyscale = GetMaskFlag(MASK2C02.GRAYSCALE) != 0;
			grey_scale_value = is_greyscale ? 0x30 : 0x3F;
			break;
		case 0x0002: // Status
			break;
		case 0x0003: // OAM Address
			oam_addr = data;
			break;
		case 0x0004: // OAM Data
			pOAM[oam_addr] = data;
			populateOAM();
			break;
		case 0x0005: // Scroll
			if (address_latch == 0)
			{
				// First write to scroll register contains X offset in pixel space
				// which we split into coarse and fine x values.
				fine_x = data & 0x07;
				tram_addr.COARSE_X = data >> 3;
				address_latch = 1;
			}
			else
			{
				// First write to scroll register contains Y offset in pixel space
				// which we split into coarse and fine Y values.
				tram_addr.FINE_Y = data & 0x07;
				tram_addr.COARSE_Y = data >> 3;
				address_latch = 0;
			}
			break;
		case 0x0006: // PPU Address
			int temp_addr = tram_addr.reg();
			if (address_latch == 0)
			{
				// PPU address bus can be accessed by CPU via the ADDR and DATA
				// registers. The first write to this register latches the high byte
				// of the address, the second is the low byte. Note the writes
				// are stored in the tram register.
				tram_addr.setreg(((data & 0x3F) << 8) | (temp_addr & 0x00FF));
				address_latch = 1;
			}
			else
			{
				// When a whole address has been written, the internal vram address
				// buffer is updated. Writing to the PPU is unwise during rendering
				// as the PPU will maintain the vram address automatically whilst
				// rendering the scanline position.
				tram_addr.setreg((temp_addr & 0xFF00) | data);
				vram_addr.copyReg(tram_addr);
				address_latch = 0;
			}
			break;
		case 0x0007: // PPU Data
			temp_addr = vram_addr.reg();
			ppuWrite(temp_addr, data);
			// All writes from PPU data automatically increment the nametable
			// address depending upon the mode set in the control register.
			// If set to vertical mode, the increment is 32, so it skips
			// one whole nametable row; in horizontal mode it just increments
			// by 1, moving to the next column.
			vram_addr.setreg(temp_addr + increment_mode_value);
			break;
		}
	}
	
	public /*unsigned 8bit*/ int cpuRead(/*unsigned 16bit*/ int addr)
	{
		return cpuRead(addr, false);
	}
	
	public /*unsigned 8bit*/ int cpuRead(/*unsigned 16bit*/ int addr, boolean bReadOnly)
	{
		/*unsigned 8bit*/ int data = 0x00;
		
		if (bReadOnly)
		{
			// Reading from PPU registers can affect their contents
			// so this read only option is used for examining the
			// state of the PPU without changing its state. This is
			// really only used in debug mode.
			switch (addr)
			{
			case 0x0000: // Control
				data = control;
				break;
			case 0x0001: // Mask
				data = mask;
				break;
			case 0x0002: // Status
				data = status;
				break;
			case 0x0003: // OAM Address
				break;
			case 0x0004: // OAM Data
				data = pOAM[oam_addr];
				break;
			case 0x0005: // Scroll
				break;
			case 0x0006: // PPU Address
				break;
			case 0x0007: // PPU Data
				break;
			}
		}
		else
		{
			// These are the live PPU registers that respond
			// to being read from in various ways. Note that not
			// all the registers are capable of being read from
			// so they just return 0x00.
			switch (addr)
			{
			case 0x0000: // Control
				// Control - Not readable
				break;
			case 0x0001: // Mask
				// Mask - Not Readable
				break;
			case 0x0002: // Status		
				// Reading from the status register has the effect of resetting
				// different parts of the circuit. Only the top three bits
				// contain status information, however it is possible that
				// some "noise" gets picked up on the bottom 5 bits which 
				// represent the last PPU bus transaction. Some games "may"
				// use this noise as valid data (even though they probably
				// shouldn't).
				data = (status & 0XE0) | (ppu_data_buffer & 0x1F);
				// Clear the vertical blanking flag
				SetStatusFlagOFF(STATUS2C02.VERTICAL_BLANK);
				// Reset Loopy's Address latch flag
				address_latch = 0;
				break;
			case 0x0003: // OAM Address
				break;
			case 0x0004: // OAM Data
				break;
			case 0x0005: // Scroll
				break;
			case 0x0006: // PPU Address
				break;
			case 0x0007: // PPU Data
				// Reads from the NameTable ram get delayed one cycle, 
				// so output buffer which contains the data from the 
				// previous read request.
				int temp_data = ppu_data_buffer;
				// Then update the buffer for next time.
				int temp_addr = vram_addr.reg();
				ppu_data_buffer = ppuRead(temp_addr);
				data = temp_data;
				
				// However, if the address was in the palette range, the
				// data is not delayed, so it returns immediately.
				if (temp_addr >= 0x3F00) data = ppu_data_buffer;
				
				// All reads from PPU data automatically increment the nametable
				// address depending upon the mode set in the control register.
				// If set to vertical mode, the increment is 32, so it skips
				// one whole nametable row; in horizontal mode it just increments
				// by 1, moving to the next column.
				vram_addr.setreg(temp_addr + increment_mode_value);
				break;
			}
		}
		
		return data;
	}
	
	// Communication with PPU Bus
	public void ppuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		addr &= 0x3FFF;
		
		if (cartridge.ppuWrite(addr, data))
		{
			
		}
		else if (addr >= 0x0000 && addr <= 0x1FFF)
		{
			// If the cartridge can't map the address, we have
			// a physical location ready here.
			// The high bit selects between the first or second 4KB
			// block of pattern memory and the other bits is an
			// offset into the selected 4KB block.
			pattern_table[(addr & 0x1000) >> 12][addr & 0x0FFF] = data;
		}
		else if (addr >= 0x2000 && addr <= 0x3EFF)
		{
			addr &= 0x0FFF;
			if (cartridge.Mirror() == MIRROR.VERTICAL)
			{
				// Vertical
				if (addr >= 0x0000 && addr <= 0x03FF)
					name_table[0][addr & 0x03FF] = data;
				if (addr >= 0x0400 && addr <= 0x07FF)
					name_table[1][addr & 0x03FF] = data;
				if (addr >= 0x0800 && addr <= 0x0BFF)
					name_table[0][addr & 0x03FF] = data;
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					name_table[1][addr & 0x03FF] = data;
			}
			else if (cartridge.Mirror() == MIRROR.HORIZONTAL)
			{
				// Horizontal
				if (addr >= 0x0000 && addr <= 0x03FF)
					name_table[0][addr & 0x03FF] = data;
				if (addr >= 0x0400 && addr <= 0x07FF)
					name_table[0][addr & 0x03FF] = data;
				if (addr >= 0x0800 && addr <= 0x0BFF)
					name_table[1][addr & 0x03FF] = data;
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					name_table[1][addr & 0x03FF] = data;
			}
		}
		else if (addr >= 0x3F00 && addr <= 0x3FFF)
		{
			// Mask the bottom 5 bits of the address.
			addr &= 0x001F;
			// Hard code the mirroring of the resultant address.
			if (addr == 0x0010) addr = 0x0000;
			if (addr == 0x0014) addr = 0x0004;
			if (addr == 0x0018) addr = 0x0008;
			if (addr == 0x001C) addr = 0x000C;
			// Read directly from the palette.
			palette[addr] = data;
		}
	}
	
	public /*unsigned 8bit*/ int ppuRead(/*unsigned 16bit*/ int addr)
	{
		return ppuRead(addr, false);
	}
	
	public /*unsigned 8bit*/ int ppuRead(/*unsigned 16bit*/ int addr, boolean bReadOnly)
	{
		addr &= 0x3FFF;
		if (cartridge.ppuRead(addr, data_by_ref))
		{
			return data_by_ref[0];
		}
		else if (addr >= 0x0000 && addr <= 0x1FFF)
		{
			// If the cartridge can't map the address, we have
			// a physical location ready here.
			// The high bit selects between the first or second 4KB
			// block of pattern memory and the other bits is an
			// offset into the selected 4KB block.
			return pattern_table[(addr & 0x1000) >> 12][addr & 0x0FFF];
		}
		else if (addr >= 0x2000 && addr <= 0x3EFF)
		{
			addr &= 0x0FFF;
			
			if (cartridge.Mirror() == MIRROR.VERTICAL)
			{
				// Vertical
				if (addr >= 0x0000 && addr <= 0x03FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0400 && addr <= 0x07FF)
					return name_table[1][addr & 0x03FF];
				if (addr >= 0x0800 && addr <= 0x0BFF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					return name_table[1][addr & 0x03FF];
			}
			else if (cartridge.Mirror() == MIRROR.HORIZONTAL)
			{
				// Horizontal
				if (addr >= 0x0000 && addr <= 0x03FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0400 && addr <= 0x07FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0800 && addr <= 0x0BFF)
					return name_table[1][addr & 0x03FF];
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					return name_table[1][addr & 0x03FF];
			}
		}
		else if (addr >= 0x3F00 && addr <= 0x3FFF)
		{
			// Mask the bottom 5 bits of the address.
			addr &= 0x001F;
			// Hard code the mirroring of the resultant address.
			if (addr == 0x0010) addr = 0x0000;
			if (addr == 0x0014) addr = 0x0004;
			if (addr == 0x0018) addr = 0x0008;
			if (addr == 0x001C) addr = 0x000C;
			// Read directly from the palette.
			return palette[addr] & grey_scale_value;
		}
		
		return data_by_ref[0];
	}
	
	public /*unsigned 8bit*/ int ppuReadPattern(/*unsigned 16bit*/ int addr)
	{
		addr &= 0x3FFF;
		if (cartridge.ppuRead(addr, data_by_ref))
		{
			return data_by_ref[0];
		}
		else
		{
			// If the cartridge can't map the address, we have
			// a physical location ready here.
			// The high bit selects between the first or second 4KB
			// block of pattern memory and the other bits is an
			// offset into the selected 4KB block.
			return pattern_table[(addr & 0x1000) >> 12][addr & 0x0FFF];
		}
	}
	
	public /*unsigned 8bit*/ int ppuReadTblName(/*unsigned 16bit*/ int addr)
	{
		addr &= 0x3FFF;
		if (cartridge.ppuRead(addr, data_by_ref))
		{
			return data_by_ref[0];
		}
		else
		{
			addr &= 0x0FFF;
			
			if (cartridge.Mirror() == MIRROR.VERTICAL)
			{
				// Vertical
				if (addr >= 0x0000 && addr <= 0x03FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0400 && addr <= 0x07FF)
					return name_table[1][addr & 0x03FF];
				if (addr >= 0x0800 && addr <= 0x0BFF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					return name_table[1][addr & 0x03FF];
			}
			else if (cartridge.Mirror() == MIRROR.HORIZONTAL)
			{
				// Horizontal
				if (addr >= 0x0000 && addr <= 0x03FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0400 && addr <= 0x07FF)
					return name_table[0][addr & 0x03FF];
				if (addr >= 0x0800 && addr <= 0x0BFF)
					return name_table[1][addr & 0x03FF];
				if (addr >= 0x0C00 && addr <= 0x0FFF)
					return name_table[1][addr & 0x03FF];
			}
		}
		
		return data_by_ref[0];
	}
	
	// Faster ppuRead when we know we're reading the palette memory only for example from the PPU.
	public /*unsigned 8bit*/ int ppuReadPalette(/*unsigned 16bit*/ int addr)
	{
		addr &= 0x3FFF;
		if (cartridge.ppuRead(addr, data_by_ref))
		{
			return data_by_ref[0];
		}
		else
		{
			// Mask the bottom 5 bits of the address.
			addr &= 0x001F;
			// Hard code the mirroring of the resultant address.
			if (addr == 0x0010) addr = 0x0000;
			if (addr == 0x0014) addr = 0x0004;
			if (addr == 0x0018) addr = 0x0008;
			if (addr == 0x001C) addr = 0x000C;
			// Read directly from the palette.
			return palette[addr] & grey_scale_value;
		}
	}
	
	// Interface
	public void ConnectCartridge(ACartridge cartridge)
	{
		this.cartridge = cartridge;
	}
	
	public void reset()
	{
		initPalette();
		
		name_table = new /*unsigned 8bit*/ int[2][1024];
		
		// Palette
		palette = new /*unsigned 8bit*/ int[32];
		
		// Two 4KB pattern tables
		pattern_table = new /*unsigned 8bit*/ int[2][4096];
		
		OAM = new ObjectAttributeEntry[64];
		for (int n=0; n < 64; n++)
		{
			OAM[n] = new ObjectAttributeEntry();
		}
		
		pOAM = new int[4 * 64];
		oam_addr = 0x00;
		
		spriteScanline = new ObjectAttributeEntry[8];
		for (int n=0; n < 8; n++)
		{
			spriteScanline[n] = new ObjectAttributeEntry();
		}
		
		sprite_shifter_pattern_lo = new int[8];
		sprite_shifter_pattern_hi = new int[8];
		
		for (int x=0; x < 2; x++)
			for (int y=0; y < 1024; y++)
				name_table[x][y] = 0;
		
		for (int x=0; x < 2; x++)
			for (int y=0; y < 4096; y++)
				pattern_table[x][y] = 0;
		
		for (int x=0; x < 32; x++)
			palette[x] = 0;
		
		for (int n=0; n < FRAMES_TO_KEEP; n++)
		{
			frames[n] = new Frame();
			frames[n].frame = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < 256; x++)
				for (int y = 0; y < 240; y++)
					frames[n].frame.setRGB(x, y, nes_palette[0]);
			frames[n].screen_data = ((DataBufferInt)frames[n].frame.getRaster().getDataBuffer()).getData();
			frames[n].frame_number = -1;
		}
		
		frame_being_drawn_screen_data = frames[0].screen_data;
		
		total_frames_drawn = 0;
		current_frame_index = 0;
		
		nmi = false;
		fine_x = 0x00;
		address_latch = 0x00;
		ppu_data_buffer = 0x00;
		scanline = -1;
		cycle = 0;
		bg_next_tile_id = 0x00;
		bg_next_tile_attrib = 0x00;
		bg_next_tile_lsb = 0x00;
		bg_next_tile_msb = 0x00;
		bg_shifter_pattern_lo = 0x0000;
		bg_shifter_pattern_hi = 0x0000;
		bg_shifter_attrib_lo = 0x0000;
		bg_shifter_attrib_hi = 0x0000;
		status = 0x00;
		mask = 0x00;
		control = 0x00;
		vram_addr = new LOOPY_REGISTER();
		tram_addr = new LOOPY_REGISTER();
		frame_complete = false;
		pattern_background_shifted_left_by_12 = 0;
		pattern_sprite_shifted_left_by_12 = 0;
		increment_mode_value = 0;
		sprite_size = 0;
		current_pixel = 0;
		sprite_zero_hit_possible = false;
		sprite_zero_being_rendered = false;
		sprite_count = 0;
		
		data_by_ref = new int[1];
		
		is_rendering_background = false;
		is_rendering_background_or_sprites = false;
		is_rendering_sprites = false;
		is_greyscale = false;
		grey_scale_value = 0x00;
	}
	
	// As we progress through scanlines and cycles, the PPU is effectively
	// a state machine going through the motions of fetching background 
	// information and sprite information, compositing them into a pixel
	// to be output.
	
	// ==============================================================================
	// Increment the background tile "pointer" one tile/column horizontally.
	private void IncrementScrollX()
	{
		// Note: pixel perfect scrolling horizontally is handled by the 
		// data shifters. Here we are operating in the spatial domain of 
		// tiles, 8x8 pixel blocks.
		
		// Only if rendering is enabled
		if (is_rendering_background_or_sprites)
		{
			// A single name table is 32x30 tiles. As we increment horizontally
			// we may cross into a neighbouring nametable, or wrap around to
			// a neighbouring nametable.
			if (vram_addr.COARSE_X == 31)
			{
				// Leaving nametable so wrap address round.
				vram_addr.COARSE_X = 0;
				// Flip target nametable bit.
				vram_addr.NAMETABLE_X = (~vram_addr.NAMETABLE_X) & 0x01;
			}
			else
			{
				// Staying in current nametable, so just increment.
				vram_addr.COARSE_X++;
			}
		}
	};
	
	// ==============================================================================
	// Increment the background tile "pointer" one scanline vertically.
	private void IncrementScrollY()
	{
		// Incrementing vertically is more complicated. The visible nametable
		// is 32x30 tiles, but in memory there is enough room for 32x32 tiles.
		// The bottom two rows of tiles are in fact not tiles at all, they
		// contain the "attribute" information for the entire table. This is
		// information that describes which palettes are used for different 
		// regions of the nametable.
		
		// In addition, the NES doesn't scroll vertically in chunks of 8 pixels
		// i.e. the height of a tile, it can perform fine scrolling by using
		// the fine_y component of the register. This means an increment in Y
		// first adjusts the fine offset, but may need to adjust the whole
		// row offset, since fine_y is a value 0 to 7, and a row is 8 pixels high.

		// Only if rendering is enabled.
		if (is_rendering_background_or_sprites)
		{
			// If possible, just increment the fine y offset.
			if (vram_addr.FINE_Y < 7)
			{
				vram_addr.FINE_Y++;
			}
			else
			{
				// If we have gone beyond the height of a row, we need to
				// increment the row, potentially wrapping into neighbouring
				// vertical nametables. Don't forget however, the bottom two rows
				// do not contain tile information. The coarse y offset is used
				// to identify which row of the nametable we want, and the fine
				// y offset is the specific "scanline".

				// Reset fine y offset.
				vram_addr.FINE_Y = 0;

				// Check if we need to swap vertical nametable targets.
				if (vram_addr.COARSE_Y == 29)
				{
					// We do, so reset coarse y offset.
					vram_addr.COARSE_Y = 0;
					// And flip the target nametable bit.
					vram_addr.NAMETABLE_Y = (~vram_addr.NAMETABLE_Y) & 0x01;
				}
				else if (vram_addr.COARSE_Y == 31)
				{
					// In case the pointer is in the attribute memory, we
					// just wrap around the current nametable.
					vram_addr.COARSE_Y = 0;
				}
				else
				{
					// None of the above boundary/wrapping conditions apply
					// so just increment the coarse y offset.
					vram_addr.COARSE_Y++;
				}
			}
		}
	};
	
	// ==============================================================================
	// Prime the "in-effect" background tile shifters ready for outputting next
	// 8 pixels in scanline.
	private void LoadBackgroundShifters()
	{	
		// Each PPU update we calculate one pixel. These shifters shift 1 bit along
		// feeding the pixel compositor with the binary information it needs. Its
		// 16 bits wide, because the top 8 bits are the current 8 pixels being drawn
		// and the bottom 8 bits are the next 8 pixels to be drawn. Naturally this means
		// the required bit is always the MSB of the shifter. However, "fine x" scrolling
		// plays a part in this too, which is seen later, so in fact we can choose
		// any one of the top 8 bits.
		bg_shifter_pattern_lo = (bg_shifter_pattern_lo & 0xFF00) | bg_next_tile_lsb;
		bg_shifter_pattern_hi = (bg_shifter_pattern_hi & 0xFF00) | bg_next_tile_msb;

		// Attribute bits do not change per pixel, rather they change every 8 pixels
		// but are synchronised with the pattern shifters for convenience, so here
		// we take the bottom 2 bits of the attribute word which represent which 
		// palette is being used for the current 8 pixels and the next 8 pixels, and 
		// "inflate" them to 8 bit words.
		bg_shifter_attrib_lo  = (bg_shifter_attrib_lo & 0xFF00) | ((bg_next_tile_attrib & 0b01) != 0 ? 0xFF : 0x00);
		bg_shifter_attrib_hi  = (bg_shifter_attrib_hi & 0xFF00) | ((bg_next_tile_attrib & 0b10) != 0 ? 0xFF : 0x00);
	};
	
	// ==============================================================================
	// Transfer the temporarily stored horizontal nametable access information
	// into the "pointer". Note that fine x scrolling is not part of the "pointer"
	// addressing mechanism.
	private void TransferAddressX()
	{
		// Only if rendering is enabled.
		if (is_rendering_background_or_sprites)
		{
			vram_addr.NAMETABLE_X = tram_addr.NAMETABLE_X;
			vram_addr.COARSE_X = tram_addr.COARSE_X;
		}
	};
	
	// ==============================================================================
	// Transfer the temporarily stored vertical nametable access information
	// into the "pointer". Note that fine y scrolling is part of the "pointer"
	// addressing mechanism.
	private void TransferAddressY()
	{
		// Only if rendering is enabled.
		if (is_rendering_background_or_sprites)
		{
			vram_addr.NAMETABLE_Y = tram_addr.NAMETABLE_Y;
			vram_addr.FINE_Y = tram_addr.FINE_Y;
			vram_addr.COARSE_Y = tram_addr.COARSE_Y;
		}
	};
	
	// ==============================================================================
	// Every cycle the shifters storing pattern and attribute information shift
	// their contents by 1 bit. This is because every cycle, the output progresses
	// by 1 pixel. This means relatively, the state of the shifter is in sync
	// with the pixels being drawn for that 8 pixel section of the scanline.
	private void UpdateShifters()
	{
		if (is_rendering_background)
		{
			// Shifting background tile pattern row.
			bg_shifter_pattern_lo <<= 1;
			bg_shifter_pattern_hi <<= 1;

			// Shifting palette attributes by 1.
			bg_shifter_attrib_lo <<= 1;
			bg_shifter_attrib_hi <<= 1;
		}
		
		if (is_rendering_sprites && cycle >= 1 && cycle < 258)
		{
			for (int n = 0; n < sprite_count; n++)
			{
				if (spriteScanline[n].draw)
				{
					sprite_shifter_pattern_lo[n] <<= 1;
					sprite_shifter_pattern_hi[n] <<= 1;
					
					spriteScanline[n].count_down--;
					if (spriteScanline[n].count_down == 0)
					{
						spriteScanline[n].draw = false;
					}
				}
			}
		}
	};
	
	public void clock()
	{
		int n;
		int color;
		
		/*unsigned 8bit*/ int sprite_pattern_bits_lo, sprite_pattern_bits_hi;
		/*unsigned 16bit*/ int sprite_pattern_addr_lo, sprite_pattern_addr_hi;
		
		/*unsigned 8bit*/ int fg_pixel_lo;
		/*unsigned 8bit*/ int fg_pixel_hi;
		
		// Composition - We now have background pixel information for this cycle.
		// At this point we are only interested in background.
		/*unsigned 8bit*/ int bg_pixel;   // The 2-bit pixel to be rendered
		/*unsigned 8bit*/ int bg_palette; // The 2-bit index of the palette the pixel indexes
		// Handle pixel selection by selecting the relevant bit
		// depending upon fine x scrolling. This has the effect of
		// offsetting ALL background rendering by a set number
		// of pixels, permitting smooth scrolling.
		/*unsigned 16bit*/ int bit_mux;
		
		// Select Plane pixels by extracting from the shifter 
		// at the required location. 
		/*unsigned 8bit*/ int p0_pixel;
		/*unsigned 8bit*/ int p1_pixel;
		
		// Get palette
		/*unsigned 8bit*/ int bg_pal0;
		/*unsigned 8bit*/ int bg_pal1;
		
		// Foreground =============================================================
		/*unsigned 8bit*/ int fg_pixel;   	// The 2-bit pixel to be rendered
		/*unsigned 8bit*/ int fg_palette; 	// The 2-bit index of the palette the pixel indexes
		/*unsigned 8bit*/ int fg_priority;	// A bit of the sprite attribute indicates if its
								   			// more important than the background.
		
		if (scanline == 0 && cycle == 0)
		{
			// "Odd Frame" cycle skip
			cycle = 1;
			current_pixel = 0;
		}

		// All but 1 of the scanlines is visible to the user. The pre-render scanline
		// at -1, is used to configure the "shifters" for the first visible scanline, 0.
		if ((scanline >= -1) && (scanline < 240))
		{			
			if (scanline == -1 && cycle == 1)
			{
				// Effectively start of new frame, so clear vertical blank flag AND
				// Clear sprite overflow flag AND
				// Clear sprite overflow flag
				SetStatusFlagOFF(vblank_soverflow_szerohit_off);
				
				// Clear Shifters
				sprite_shifter_pattern_lo[0] = 0;
				sprite_shifter_pattern_hi[0] = 0;
				sprite_shifter_pattern_lo[1] = 0;
				sprite_shifter_pattern_hi[1] = 0;
				sprite_shifter_pattern_lo[2] = 0;
				sprite_shifter_pattern_hi[2] = 0;
				sprite_shifter_pattern_lo[3] = 0;
				sprite_shifter_pattern_hi[3] = 0;
				sprite_shifter_pattern_lo[4] = 0;
				sprite_shifter_pattern_hi[4] = 0;
				sprite_shifter_pattern_lo[5] = 0;
				sprite_shifter_pattern_hi[5] = 0;
				sprite_shifter_pattern_lo[6] = 0;
				sprite_shifter_pattern_hi[6] = 0;
				sprite_shifter_pattern_lo[7] = 0;
				sprite_shifter_pattern_hi[7] = 0;
			}

			if ((cycle >= 2 && cycle < 258) || (cycle >= 321 && cycle < 338))
			{
				UpdateShifters();
				
				// In these cycles we are collecting and working with visible data
				// The "shifters" have been preloaded by the end of the previous
				// scanline with the data for the start of this scanline. Once we
				// leave the visible region, we go dormant until the shifters are
				// preloaded for the next scanline.

				// Fortunately, for background rendering, we go through a fairly
				// repeatable sequence of events, every 2 clock cycles.
				switch ((cycle - 1) % 8)
				{
				case 0:
					// Load the current background tile pattern and attributes into the "shifter"
					LoadBackgroundShifters();
					
					// Fetch the next background tile ID
					// "vram_addr.significant12Bits()" 	: Mask to 12 bits that are relevant
					// "| 0x2000"                 		: Offset into nametable space on PPU address bus
					bg_next_tile_id = ppuReadTblName(0x2000 | vram_addr.significant12Bits());
					
					// Explanation:
					// The bottom 12 bits of the loopy register provide an index into
					// the 4 nametables, regardless of nametable mirroring configuration.
					// nametable_y(1) nametable_x(1) coarse_y(5) coarse_x(5)
					//
					// Consider a single nametable is a 32x32 array, and we have four of them
					//   0                1
					// 0 +----------------+----------------+
					//   |                |                |
					//   |                |                |
					//   |    (32x32)     |    (32x32)     |
					//   |                |                |
					//   |                |                |
					// 1 +----------------+----------------+
					//   |                |                |
					//   |                |                |
					//   |    (32x32)     |    (32x32)     |
					//   |                |                |
					//   |                |                |
					//   +----------------+----------------+
					//
					// This means there are 4096 potential locations in this array, which 
					// just so happens to be 2^12.
					break;
				case 2:
					// Fetch the next background tile attribute. OK, so this one is a bit
					// more involved.

					// Each nametable has two rows of cells that are not tile 
					// information, instead they represent the attribute information that
					// indicates which palettes are applied to which area on the screen.
					// Importantly there is not a 1 to 1 correspondence
					// between background tile and palette. Two rows of tile data holds
					// 64 attributes. Therefore we can assume that the attributes affect
					// 8x8 zones on the screen for that nametable. Given a working resolution
					// of 256x240, we can further assume that each zone is 32x32 pixels
					// in screen space, or 4x4 tiles. Four system palettes are allocated
					// to background rendering, so a palette can be specified using just
					// 2 bits. The attribute byte therefore can specify 4 distinct palettes.
					// Therefore we can even further assume that a single palette is
					// applied to a 2x2 tile combination of the 4x4 tile zone. The very fact
					// that background tiles "share" a palette locally is the reason why
					// in some games you see distortion in the colours at screen edges.

					// As before when choosing the tile ID, we can use the bottom 12 bits of
					// the loopy register, but we need to make the implementation "coarser"
					// because instead of a specific tile, we want the attribute byte for a 
					// group of 4x4 tiles, or in other words, we divide our 32x32 address
					// by 4 to give us an equivalent 8x8 address, and we offset this address
					// into the attribute section of the target nametable.

					// Reconstruct the 12 bit loopy address into an offset into the
					// attribute memory

					// "(vram_addr.coarse_x >> 2)"        : integer divide coarse x by 4, 
					//                                      from 5 bits to 3 bits
					// "((vram_addr.coarse_y >> 2) << 3)" : integer divide coarse y by 4, 
					//                                      from 5 bits to 3 bits,
					//                                      shift to make room for coarse x

					// Result so far: YX00 00yy yxxx

					// All attribute memory begins at 0x03C0 within a nametable, so OR with
					// result to select target nametable, and attribute byte offset. Finally
					// OR with 0x2000 to offset into nametable address space on PPU bus.
					bg_next_tile_attrib = ppuReadTblName(
						0x23C0
						| (vram_addr.NAMETABLE_Y << 11)
						| (vram_addr.NAMETABLE_X << 10)
						| ((vram_addr.COARSE_Y >> 2) << 3)
						| (vram_addr.COARSE_X >> 2));
					// Right we've read the correct attribute byte for a specified address,
					// but the byte itself is broken down further into the 2x2 tile groups
					// in the 4x4 attribute zone.

					// The attribute byte is assembled thus: BR(76) BL(54) TR(32) TL(10)
					//
					// +----+----+			    +----+----+
					// | TL | TR |			    | ID | ID |
					// +----+----+ where TL =   +----+----+
					// | BL | BR |			    | ID | ID |
					// +----+----+			    +----+----+
					//
					// Since we know we can access a tile directly from the 12 bit address, we
					// can analyze the bottom bits of the coarse coordinates to provide us with
					// the correct offset into the 8-bit word, to yield the 2 bits we are
					// actually interested in which specifies the palette for the 2x2 group of
					// tiles. We know if "coarse y % 4" < 2 we are in the top half else bottom half.
					// Likewise if "coarse x % 4" < 2 we are in the left half else right half.
					// Ultimately we want the bottom two bits of our attribute word to be the
					// palette selected, so shift as required.		
					if ((vram_addr.COARSE_Y & 0x02) != 0) bg_next_tile_attrib >>= 4;
					if ((vram_addr.COARSE_X & 0x02) != 0) bg_next_tile_attrib >>= 2;
					bg_next_tile_attrib &= 0x03;
					break;
				case 4: 
					// Fetch the next background tile LSB bit plane from the pattern memory.
					// The tile ID has been read from the nametable. We will use this id to 
					// index into the pattern memory to find the correct sprite (assuming
					// the sprites lie on 8x8 pixel boundaries in that memory, which they do
					// even though 8x16 sprites exist, as background tiles are always 8x8).
					//
					// Since the sprites are effectively 1 bit deep, but 8 pixels wide, we 
					// can represent a whole sprite row as a single byte, so offsetting
					// into the pattern memory is easy. In total there is 8KB so we need a 
					// 13 bit address.

					// "(control.pattern_background << 12)"  : the pattern memory selector 
					//                                         from control register, either 0K
					//                                         or 4K offset
					// "((uint16_t)bg_next_tile_id << 4)"    : the tile id multiplied by 16, as
					//                                         2 lots of 8 rows of 8 bit pixels
					// "(vram_addr.fine_y)"                  : Offset into which row based on
					//                                         vertical scroll offset
					// Note: No PPU address bus offset required as it starts at 0x0000
					bg_next_tile_lsb = ppuReadPattern(
						(
						(pattern_background_shifted_left_by_12)
						+ (bg_next_tile_id << 4)
						+ (vram_addr.FINE_Y)
						)
						);
					break;
				case 6:
					bg_next_tile_msb = ppuReadPattern(
						(
						(pattern_background_shifted_left_by_12)
						+ (bg_next_tile_id << 4)
						+ (vram_addr.FINE_Y) + 8
						)
						);
					break;
				case 7:
					IncrementScrollX();
					break;
				}
			}
			
			if (cycle == 256)
			{
				IncrementScrollY();
			}
			
			if (cycle == 257)
			{
				LoadBackgroundShifters();
				TransferAddressX();
			}
			
			// Superfluous reads of tile id at end of scanline.
			if (cycle == 338 || cycle == 340)
			{
				bg_next_tile_id = ppuReadTblName(0x2000 | vram_addr.significant12Bits());
			}
			
			if (scanline == -1 && cycle >= 280 && cycle < 305)
			{
				TransferAddressY();
			}
			
			// Foreground Rendering ========================================================
			// The PPU loads sprite information successively during the region that
			// background tiles are not being drawn. Instead, I'm going to perform
			// all sprite evaluation in one hit. THE NES DOES NOT DO IT LIKE THIS! This makes
			// it easier to see the process of sprite evaluation.
			if (cycle == 257 && scanline >= 0)
			{
				// We've reached the end of a visible scanline. It is now time to determine
				// which sprites are visible on the next scanline, and preload this info
				// into buffers that we can work with while the scanline scans the row.

				// Firstly, clear out the sprite memory. This memory is used to store the
				// sprites to be rendered. It is not the OAM.
				spriteScanline[0].clear();
				spriteScanline[1].clear();
				spriteScanline[2].clear();
				spriteScanline[3].clear();
				spriteScanline[4].clear();
				spriteScanline[5].clear();
				spriteScanline[6].clear();
				spriteScanline[7].clear();
				
				// The NES supports a maximum number of sprites per scanline,
				// this is 8 or fewer sprites. This is why in some games you see sprites
				// flicker or disappear when the scene gets busy.
				sprite_count = 0;
				
				// Secondly, clear out any residual information in sprite pattern shifters.
				sprite_shifter_pattern_lo[0] = 0;
				sprite_shifter_pattern_hi[0] = 0;
				sprite_shifter_pattern_lo[1] = 0;
				sprite_shifter_pattern_hi[1] = 0;
				sprite_shifter_pattern_lo[2] = 0;
				sprite_shifter_pattern_hi[2] = 0;
				sprite_shifter_pattern_lo[3] = 0;
				sprite_shifter_pattern_hi[3] = 0;
				sprite_shifter_pattern_lo[4] = 0;
				sprite_shifter_pattern_hi[4] = 0;
				sprite_shifter_pattern_lo[5] = 0;
				sprite_shifter_pattern_hi[5] = 0;
				sprite_shifter_pattern_lo[6] = 0;
				sprite_shifter_pattern_hi[6] = 0;
				sprite_shifter_pattern_lo[7] = 0;
				sprite_shifter_pattern_hi[7] = 0;
				
				// Thirdly, Evaluate which sprites are visible in the next scanline. We need
				// to iterate through the OAM until we have found 8 sprites that have Y-positions
				// and heights that are within vertical range of the next scanline. Once we have
				// found 8 or exhausted the OAM we stop. Now, notice I count to 9 sprites. This
				// is so I can set the sprite overflow flag in the event of there being > 8 sprites.
				/*unsigned 8bit*/ int nOAMEntry = 0;
				
				// New set of sprites. Sprite zero may not exist in the new set, so clear this
				// flag.
				sprite_zero_hit_possible = false;
				
				while (nOAMEntry < 64 && sprite_count < 9)
				{
					// Note the conversion to signed numbers here
					/*unsigned 16bit*/ int diff = scanline - OAM[nOAMEntry].y;
					
					// If the difference is positive then the scanline is at least at the
					// same height as the sprite, so check if it resides in the sprite vertically
					// depending on the current "sprite height mode".
					if (diff >= 0 && diff < sprite_size)
					{
						// Sprite is visible, so copy the attribute entry over to our
						// scanline sprite cache. I added < 8 here to guard the array
						// being written to.
						if (sprite_count < 8)
						{
							// Is this sprite sprite zero?
							if (nOAMEntry == 0)
							{
								// It is, so its possible it may trigger a 
								// sprite zero hit when drawn
								sprite_zero_hit_possible = true;
							}
							
							spriteScanline[sprite_count].attribute = OAM[nOAMEntry].attribute;
							spriteScanline[sprite_count].id = OAM[nOAMEntry].id;
							spriteScanline[sprite_count].x = OAM[nOAMEntry].x;
							spriteScanline[sprite_count].y = OAM[nOAMEntry].y;
							sprite_count++;
						}
					}
					nOAMEntry++;
				}
			} // End of sprite evaluation for next scanline.
			
			// Set sprite overflow flag
			SetStatusFlag(STATUS2C02.SPRITE_OVERFLOW, sprite_count > 8);
			
			// Now we have an array of the 8 visible sprites for the next scanline. By 
			// the nature of this search, they are also ranked in priority, because
			// those lower down in the OAM have the higher priority.

			// We also guarantee that "Sprite Zero" will exist in spriteScanline[0] if
			// it is evaluated to be visible. 
			
			if (cycle == 340)
			{
				// Now we're at the very end of the scanline, Prepare the 
				// sprite shifters with the 8 or less selected sprites.
				for (n = 0; n < sprite_count; n++)
				{
					// We need to extract the 8-bit row patterns of the sprite with the
					// correct vertical offset. The "Sprite Mode" also affects this as
					// the sprites may be 8 or 16 rows high. Additionally, the sprite
					// can be flipped both vertically and horizontally.
					
					// Determine the memory addresses that contain the byte of pattern data. We
					// only need the lo pattern address, because the hi pattern address is always
					// offset by 8 from the lo address.
					if (sprite_size == 8)
					{
						// 8x8 Sprite Mode - The control register determines the pattern table.
						if (!((spriteScanline[n].attribute & 0x80) != 0))
						{
							// Sprite is NOT flipped vertically, i.e. normal    
							sprite_pattern_addr_lo = 
								  (pattern_sprite_shifted_left_by_12  )  // Which Pattern Table? 0KB or 4KB offset
								| (spriteScanline[n].id   << 4   )  // Which Cell? Tile ID * 16 (16 bytes per tile)
								| (scanline - spriteScanline[n].y); // Which Row in cell? (0->7)
						}
						else
						{
							// Sprite is flipped vertically, i.e. upside down
							sprite_pattern_addr_lo = 
								  (pattern_sprite_shifted_left_by_12  )  // Which Pattern Table? 0KB or 4KB offset
								| (spriteScanline[n].id   << 4   )  // Which Cell? Tile ID * 16 (16 bytes per tile)
								| (7 - (scanline - spriteScanline[n].y)); // Which Row in cell? (0->7)
						}
					}
					else
					{
						// 8x16 Sprite Mode - The sprite attribute determines the pattern table
						if (!((spriteScanline[n].attribute & 0x80) != 0))
						{
							// Sprite is NOT flipped vertically, i.e. normal
							if (scanline - spriteScanline[n].y < 8)
							{
								// Reading Top half Tile
								sprite_pattern_addr_lo = 
								  ((spriteScanline[n].id & 0x01)      << 12)  // Which Pattern Table? 0KB or 4KB offset
								| ((spriteScanline[n].id & 0xFE)      << 4 )  // Which Cell? Tile ID * 16 (16 bytes per tile)
								| ((scanline - spriteScanline[n].y) & 0x07 ); // Which Row in cell? (0->7)
							}
							else
							{
								// Reading Bottom Half Tile
								sprite_pattern_addr_lo = 
								  ( (spriteScanline[n].id & 0x01)      << 12)  // Which Pattern Table? 0KB or 4KB offset
								| (((spriteScanline[n].id & 0xFE) + 1) << 4 )  // Which Cell? Tile ID * 16 (16 bytes per tile)
								| ((scanline - spriteScanline[n].y) & 0x07  ); // Which Row in cell? (0->7)
							}
						}
						else
						{
							// Sprite is flipped vertically, i.e. upside down
							if (scanline - spriteScanline[n].y < 8)
							{
								// Reading Top half Tile
								sprite_pattern_addr_lo = 
								  ( (spriteScanline[n].id & 0x01)      << 12)    // Which Pattern Table? 0KB or 4KB offset
								| (((spriteScanline[n].id & 0xFE) + 1) << 4 )    // Which Cell? Tile ID * 16 (16 bytes per tile)
								| (7 - (scanline - spriteScanline[n].y) & 0x07); // Which Row in cell? (0->7)
							}
							else
							{
								// Reading Bottom Half Tile
								sprite_pattern_addr_lo = 
								  ((spriteScanline[n].id & 0x01)       << 12)    // Which Pattern Table? 0KB or 4KB offset
								| ((spriteScanline[n].id & 0xFE)       << 4 )    // Which Cell? Tile ID * 16 (16 bytes per tile)
								| (7 - (scanline - spriteScanline[n].y) & 0x07); // Which Row in cell? (0->7)
							}
						}
					}

					// High bit plane equivalent is always offset by 8 bytes from low bit plane.
					sprite_pattern_addr_hi = sprite_pattern_addr_lo + 8;
					
					// Now we have the address of the sprite patterns, we can read them
					sprite_pattern_bits_lo = ppuReadPattern(sprite_pattern_addr_lo);
					sprite_pattern_bits_hi = ppuReadPattern(sprite_pattern_addr_hi);
					
					// If the sprite is flipped horizontally, we need to flip the 
					// pattern bytes. 
					if ((spriteScanline[n].attribute & 0x40) != 0)
					{
						// Flip Patterns Horizontally
						sprite_pattern_bits_lo = flipbyte(sprite_pattern_bits_lo);
						sprite_pattern_bits_hi = flipbyte(sprite_pattern_bits_hi);
					}
					
					// Finally we can load the pattern into our sprite shift registers
					// ready for rendering on the next scanline.
					sprite_shifter_pattern_lo[n] = sprite_pattern_bits_lo;
					sprite_shifter_pattern_hi[n] = sprite_pattern_bits_hi;
				}
			}
		}
		
		if (scanline == 240)
		{
			// Nothing happens on this scanline.
		}
		
		if (scanline >= 241 && scanline < 261)
		{
			if (scanline == 241 && cycle == 1)
			{
				// Effectively end of frame, so set vertical blank flag.
				SetStatusFlagON(STATUS2C02.VERTICAL_BLANK);
				
				// If the control register tells us to emit a NMI when
				// entering vertical blanking period, do it. The CPU
				// will be informed that rendering is complete so it can
				// perform operations with the PPU knowing it wont
				// produce visible artifacts
				if (GetPPUCtrlFlag(PPUCTRL2C02.ENABLE_NMI) != 0)
				{
					nmi = true;
				}
			}
		}
		
		// Composition - We now have background pixel information for this cycle
		// At this point we are only interested in background
		/*unsigned 8bit*/ bg_pixel = 0x00;   // The 2-bit pixel to be rendered
		/*unsigned 8bit*/ bg_palette = 0x00; // The 2-bit index of the palette the pixel indexes
		if (is_rendering_background)
		{
			// Handle Pixel Selection by selecting the relevant bit
			// depending upon fine x scrolling. This has the effect of
			// offsetting all background rendering by a set number
			// of pixels, permitting smooth scrolling.
			/*unsigned 16bit*/ bit_mux = 0x8000 >> fine_x;
			
			// Select Plane pixels by extracting from the shifter 
			// at the required location. 
			/*unsigned 8bit*/ p0_pixel = (bg_shifter_pattern_lo & bit_mux) != 0 ? 1 : 0;
			/*unsigned 8bit*/ p1_pixel = (bg_shifter_pattern_hi & bit_mux) != 0 ? 2 : 0;
			
			// Combine to form pixel index
			bg_pixel = (p1_pixel) | p0_pixel;
			
			// Get palette
			/*unsigned 8bit*/ bg_pal0 = (bg_shifter_attrib_lo & bit_mux) != 0 ? 1 : 0;
			/*unsigned 8bit*/ bg_pal1 = (bg_shifter_attrib_hi & bit_mux) != 0 ? 2 : 0;
			bg_palette = (bg_pal1) | bg_pal0;
		}
		
		// Foreground =============================================================
		/*unsigned 8bit*/ fg_pixel = 0x00;   	// The 2-bit pixel to be rendered
		/*unsigned 8bit*/ fg_palette = 0x00; 	// The 2-bit index of the palette the pixel indexes
		/*unsigned 8bit*/ fg_priority = 0x00;	// A bit of the sprite attribute indicates if its
								   				// more important than the background
		
		if (is_rendering_sprites)
		{
			// Iterate through all sprites for this scanline. This is to maintain
			// sprite priority. As soon as we find a non transparent pixel of
			// a sprite we can abort.
			
			sprite_zero_being_rendered = false;
			
			for (/*unsigned 8bit*/ n = 0; n < sprite_count; n++)
			{
				// Scanline cycle has "collided" with sprite, shifters taking over.
				if (spriteScanline[n].x == cycle - 1)
				{
					spriteScanline[n].draw = true;
				}
			}
			
			for (/*unsigned 8bit*/ n = 0; n < sprite_count; n++)
			{				
				if (spriteScanline[n].draw)
				{
					// Note fine X scrolling does not apply to sprites, the game
					// should maintain their relationship with the background. So
					// we'll just use the MSB of the shifter.
					
					// Determine the pixel value.
					/*unsigned 8bit*/ fg_pixel_lo = (sprite_shifter_pattern_lo[n] & 0x80) != 0 ? 1 : 0;
					/*unsigned 8bit*/ fg_pixel_hi = (sprite_shifter_pattern_hi[n] & 0x80) != 0 ? 2 : 0;
					fg_pixel = (fg_pixel_hi) | fg_pixel_lo;
					
					// Extract the palette from the bottom two bits.
					// Foreground palettes are the latter 4 in the 
					// palette memory.
					fg_palette = (spriteScanline[n].attribute & 0x03) + 0x04;
					fg_priority = (spriteScanline[n].attribute & 0x20) == 0 ? 1 : 0;
					
					// If pixel is not transparent, we render it, and don't
					// bother checking the rest because the earlier sprites
					// in the list are higher priority.
					if (fg_pixel != 0)
					{
						if (n == 0) // Is this sprite zero?
						{
							sprite_zero_being_rendered = true;
						}

						break;
					}	
				}
			}
		}
		
		// Now we have a background pixel and a foreground pixel. They need
		// to be combined. It is possible for sprites to go behind background
		// tiles that are not "transparent".

		/*unsigned 8bit*/ int pixel = 0x00;   // The final pixel...
		/*unsigned 8bit*/ int palette = 0x00; // The final palette...
		
		if (bg_pixel == 0 && fg_pixel == 0)
		{
			// The background pixel is transparent
			// The foreground pixel is transparent
			// No winner, draw "background" colour.
			pixel = 0x00;
			palette = 0x00;
		}
		else if (bg_pixel == 0 && fg_pixel != 0)
		{
			// The background pixel is transparent
			// The foreground pixel is visible
			// Foreground wins.
			pixel = fg_pixel;
			palette = fg_palette;
		}
		else if (bg_pixel > 0 && fg_pixel == 0)
		{
			// The background pixel is visible
			// The foreground pixel is transparent
			// Background wins.
			pixel = bg_pixel;
			palette = bg_palette;
		}
		else if (bg_pixel > 0 && fg_pixel != 0)
		{
			// The background pixel is visible
			// The foreground pixel is visible
			if (fg_priority != 0)
			{
				// Foreground has higher priority.
				pixel = fg_pixel;
				palette = fg_palette;
			}
			else
			{
				// Background has higher priority.
				pixel = bg_pixel;
				palette = bg_palette;
			}
			
			// Sprite Zero Hit detection
			if (sprite_zero_hit_possible && sprite_zero_being_rendered)
			{
				// Sprite zero is a collision between foreground and background
				// so they must both be enabled.
				if (is_rendering_background_or_sprites)
				{
					// The left edge of the screen has specific switches to control
					// its appearance. This is used to smooth inconsistencies when
					// scrolling (since sprites x coord must be >= 0).
					if ((~GetMaskFlag(MASK2C02.RENDER_BACKGROUND_LEFT) | GetMaskFlag(MASK2C02.RENDER_SPRITES_LEFT)) != 0)
					{
						if (cycle >= 9 && cycle < 258)
						{
							SetStatusFlagON(STATUS2C02.SPRITE_ZERO_HIT);
						}
					}
					else
					{
						if (cycle >= 1 && cycle < 258)
						{
							SetStatusFlagON(STATUS2C02.SPRITE_ZERO_HIT);
						}
					}
				}
			}			
		}
		
		// Now we have a final pixel colour, and a palette for this cycle
		// of the current scanline. Draw the pixel.
		if ((cycle >= 0) && (cycle < 256) && (scanline >= 0) && (scanline < 240))
		{
			color = nes_palette[ppuReadPalette(0x3F00 + (palette << 2) + pixel) & 0x3F];
			frame_being_drawn_screen_data[current_pixel] = color;
			current_pixel++;
		}
		
		// Advance renderer
		cycle++;
		if (cycle == 341)
		{
			cycle = 0;
			scanline++;
			if (scanline == 261)
			{
				scanline = -1;
				synchronized (this)
				{
					frame_complete = true;
					// Make the frame that was just drawn available in the list of frames.
					frames[current_frame_index].frame_number = total_frames_drawn;
					
					// Increment the current frame index into the list of frames we're keeping, looping
					// when at the end.
					current_frame_index++;
					current_frame_index %= FRAMES_TO_KEEP;
					
					// Set the frame being drawn to the oldest frame i.e. this is the frame we will be overwriting.
					frame_being_drawn_screen_data = frames[current_frame_index].screen_data;
					
					// Increment the total frames drawn.
					total_frames_drawn++;
					
					// Reset the pixel position back because we're starting to draw a new frame.
					current_pixel = 0;
				}
			}
		}
	}
	
	// This little function "flips" a byte
	// so 0b11100000 becomes 0b00000111. It's very
	// clever, and stolen completely from here:
	// https://stackoverflow.com/a/2602885
	private static int flipbyte(/*unsigned 8bit*/ int b)
	{
		b = (b & 0xF0) >> 4 | (b & 0x0F) << 4;
		b = (b & 0xCC) >> 2 | (b & 0x33) << 2;
		b = (b & 0xAA) >> 1 | (b & 0x55) << 1;
		return b;
	};
	
	// Get the contents of a frame from a frame number. Also respond with some additional information i.e. was the frame
	// retrieved and the last frame number that the emulation rendered.
	// This information is used by the video processor to render frames accurately.
	public synchronized void getFrame(int frame_number, Frame[] current_frame, boolean[] frame_retrieved, int[] last_frame_number)
	{
		for (int n=0; n < FRAMES_TO_KEEP; n++)
		{
			if (frames[n].frame_number == frame_number)
			{
				last_frame_number[0] = total_frames_drawn - 1;
				current_frame[0] = frames[n];
				frame_retrieved[0] = true;
				return;
			}
		}
		
		last_frame_number[0] = total_frames_drawn - 1;
		frame_retrieved[0] = false;
	}

	// Debug function
	public int GetColourFromPaletteRAM(int palette, int pixel)
	{
		// This is a convenience function that takes a specified palette and pixel
		// index and returns the appropriate screen colour.
		// "0x3F00"       - Offset into PPU addressable range where palettes are stored
		// "palette << 2" - Each palette is 4 bytes in size
		// "pixel"        - Each pixel index is either 0, 1, 2 or 3
		// "& 0x3F"       - Stops us reading beyond the bounds of the palScreen array
		return nes_palette[ppuRead(0x3F00 + (palette << 2) + pixel) & 0x3F];
		
		// Note: We don't access tblPalette directly here, instead we know that ppuRead()
		// will map the address onto the separate small RAM attached to the PPU bus.
	}
	
	// Debug function
	public Sprite GetPatternTable(int i, int palette)
	{
		// Loop through all 16x16 tiles
		for (int nTileY = 0; nTileY < 16; nTileY++)
		{
			for (int nTileX = 0; nTileX < 16; nTileX++)
			{
				// Convert the 2D tile coordinate into a 1D offset into the pattern
				// table memory.
				/*uint16_t*/ int nOffset = nTileY * 256 + nTileX * 16;
				
				// Now loop through 8 rows of 8 pixels
				for (int row = 0; row < 8; row++)
				{
					// For each row, we need to read both bit planes of the character
					// in order to extract the least significant and most significant 
					// bits of the 2 bit pixel value in the CHR ROM, each character
					// is stored as 64 bits of LSB, followed by 64 bits of MSB. This
					// conveniently means that two corresponding rows are always 8
					// bytes apart in memory.
					/*unsigned 8bit*/ int tile_lsb = ppuRead(i * 0x1000 + nOffset + row + 0);
					/*unsigned 8bit*/ int tile_msb = ppuRead(i * 0x1000 + nOffset + row + 8);
					
					// Now we have a single row of the two bit planes for the character
					// we need to iterate through the 8-bit words, combining them to give
					// us the final pixel index.
					for (int col = 0; col < 8; col++)
					{
						// We can get the index value by simply adding the bits together
						// but we're only interested in the LSB of the row words because.
						/*unsigned 8bit*/ int pixel = ((tile_lsb & 0x01) << 1) | (tile_msb & 0x01);
						
						// We will shift the row words 1 bit right for each column of
						// the character.
						tile_lsb >>= 1;
						tile_msb >>= 1;

						// Now we know the location and NES pixel value for a specific location
						// in the pattern table, we can translate that to a screen colour, and an
						// (x,y) location in the sprite.
						pattern_table_debug[i].data
							[nTileX * 8 + (7 - col)]
							[nTileY * 8 + row] = GetColourFromPaletteRAM(palette, pixel);
					}
				}
			}
		}
		
		return pattern_table_debug[i];
	}
	
	// Debug function
	public BufferedImage GetPalette(int palette)
	{
		BufferedImage return_image = new BufferedImage(16, 4, BufferedImage.TYPE_INT_RGB);
		// This is a convenience function that takes a specified palette and pixel
		// index and returns the appropriate screen colour.
		// "0x3F00"       - Offset into PPU addressable range where palettes are stored
		// "palette << 2" - Each palette is 4 bytes in size
		// "pixel"        - Each pixel index is either 0, 1, 2 or 3
		// "& 0x3F"       - Stops us reading beyond the bounds of the palScreen array
		for (int pixel = 0; pixel < 4; pixel++)
		{
			 int color = nes_palette[ppuRead(0x3F00 + (palette << 2) + pixel) & 0x3F];
			 return_image.setRGB(pixel*4, 0, color);
			 return_image.setRGB(pixel*4+1, 0, color);
			 return_image.setRGB(pixel*4+2, 0, color);
			 return_image.setRGB(pixel*4+3, 0, color);
			 return_image.setRGB(pixel*4, 1, color);
			 return_image.setRGB(pixel*4+1, 1, color);
			 return_image.setRGB(pixel*4+2, 1, color);
			 return_image.setRGB(pixel*4+3, 1, color);
			 return_image.setRGB(pixel*4, 2, color);
			 return_image.setRGB(pixel*4+1, 2, color);
			 return_image.setRGB(pixel*4+2, 2, color);
			 return_image.setRGB(pixel*4+3, 2, color);
			 return_image.setRGB(pixel*4, 3, color);
			 return_image.setRGB(pixel*4+1, 3, color);
			 return_image.setRGB(pixel*4+2, 3, color);
			 return_image.setRGB(pixel*4+3, 3, color);
		}
		return return_image;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//FLAG FUNCTIONS
	
	// Sets or clears a specific bit of the status register.
	private void SetStatusFlag(/*unsigned 8bit*/ int f, boolean v)
	{
		if (v)
			status |= f;
		else
			status &= ~f;
	}
	
	// Sets a specific bit of the status register.
	private void SetStatusFlagON(/*unsigned 8bit*/ int f)
	{
		status |= f;
	}
	
	// Clears a specific bit of the status register.
	private void SetStatusFlagOFF(/*unsigned 8bit*/ int f)
	{
		status &= ~f;
	}
	
	// Returns the value of a specific bit of the mask register.
	public /*unsigned 8bit*/ int GetMaskFlag(/*unsigned 8bit*/ int f)
	{
		return mask & f;
	}
	
	// Returns the value of a specific bit of the ppuctrl register.
	public /*unsigned 8bit*/ int GetPPUCtrlFlag(/*unsigned 8bit*/ int f)
	{
		return control & f;
	}
	
	public /*unsigned 8bit*/ int GetPatternBackGroundIfSet()
	{
		if (GetPPUCtrlFlag(PPUCTRL2C02.PATTERN_BACKGROUND) != 0)
		{
			return 1 << 12;
		}
		else
		{
			return 0;
		}
	}
	
	public /*unsigned 8bit*/ int GetPatternSpriteIfSet()
	{
		if (GetPPUCtrlFlag(PPUCTRL2C02.PATTERN_SPRITE) != 0)
		{
			return 1 << 12;
		}
		else
		{
			return 0;
		}
	}
	
	public static class STATUS2C02
	{
		public static final /*unsigned 8bit*/ int UNUSED = 0;
		public static final /*unsigned 8bit*/ int SPRITE_OVERFLOW = (1 << 5);
		public static final /*unsigned 8bit*/ int SPRITE_ZERO_HIT = (1 << 6);
		public static final /*unsigned 8bit*/ int VERTICAL_BLANK = (1 << 7);
	}
	
	public static class MASK2C02
	{
		public static final /*unsigned 8bit*/ int GRAYSCALE = (1 << 0);
		public static final /*unsigned 8bit*/ int RENDER_BACKGROUND_LEFT = (1 << 1);
		public static final /*unsigned 8bit*/ int RENDER_SPRITES_LEFT = (1 << 2);
		public static final /*unsigned 8bit*/ int RENDER_BACKGROUND = (1 << 3);
		public static final /*unsigned 8bit*/ int RENDER_SPRITES = (1 << 4);
		public static final /*unsigned 8bit*/ int ENHANCE_RED = (1 << 5);
		public static final /*unsigned 8bit*/ int ENHANCE_GREEN = (1 << 6);
		public static final /*unsigned 8bit*/ int ENHANCE_BLUE = (1 << 7);
	}
	
	public static class PPUCTRL2C02
	{
		public static final /*unsigned 8bit*/ int NAMETABLE_X = (1 << 0);
		public static final /*unsigned 8bit*/ int NAMETABLE_Y = (1 << 1);
		public static final /*unsigned 8bit*/ int INCREMENT_MODE = (1 << 2);
		public static final /*unsigned 8bit*/ int PATTERN_SPRITE = (1 << 3);
		public static final /*unsigned 8bit*/ int PATTERN_BACKGROUND = (1 << 4);
		public static final /*unsigned 8bit*/ int SPRITE_SIZE = (1 << 5);
		public static final /*unsigned 8bit*/ int SLAVE_MODE = (1 << 6); // unused
		public static final /*unsigned 8bit*/ int ENABLE_NMI = (1 << 7);
	}
	
	public static class LOOPY_REGISTER
	{	
		public int COARSE_X = 0;
		public int COARSE_Y = 0;
		public int NAMETABLE_X = 0;
		public int NAMETABLE_Y = 0;
		public int FINE_Y = 0;
		public int UNUSED = 0;
		
		/*
		COARSE_X // 5 bits
		COARSE_Y // 5 bits
		NAMETABLE_X // 1 bit
		NAMETABLE_Y // 1 bit
		FINE_Y // 3 bits
		UNUSED // 1 bit
		*/
		
		int return_int;
		
		public int reg()
		{
			return_int = COARSE_X;
			return_int |= (COARSE_Y << 5);
			return_int |= (NAMETABLE_X << 10);
			return_int |= (NAMETABLE_Y << 11);
			return_int |= (FINE_Y << 12);
			return return_int;
		}
		
		public int significant12Bits()
		{
			return_int = COARSE_X;
			return_int |= (COARSE_Y << 5);
			return_int |= (NAMETABLE_X << 10);
			return_int |= (NAMETABLE_Y << 11);
			return return_int;
		}
		
		public void setreg(int value)
		{
			COARSE_X    = value & 0b0000000000011111;
			COARSE_Y    = (value & 0b0000001111100000) >> 5;
			NAMETABLE_X = (value & 0b0000010000000000) >> 10;
			NAMETABLE_Y = (value & 0b0000100000000000) >> 11;
			FINE_Y      = (value & 0b0111000000000000) >> 12;
		}
		
		public void copyReg(LOOPY_REGISTER value)
		{
			COARSE_X = value.COARSE_X;
			COARSE_Y = value.COARSE_Y;
			NAMETABLE_X = value.NAMETABLE_X;
			NAMETABLE_Y = value.NAMETABLE_Y;
			FINE_Y = value.FINE_Y;
		}
		
		public void invertNAMETABLE_X()
		{
			NAMETABLE_X = (~NAMETABLE_X) & 0x01;
		}
		
		public void invertNAMETABLE_Y()
		{
			NAMETABLE_Y = (~NAMETABLE_Y) & 0x01;
		}
	}
	
	public void populateOAM()
	{
		int counter = 0;
		for (int n=0; n < 64; n++)
		{
			OAM[n].y = pOAM[counter];
			counter++;
			OAM[n].id = pOAM[counter];
			counter++;
			OAM[n].attribute = pOAM[counter];
			counter++;
			OAM[n].x = pOAM[counter];
			counter++;
		}
	}
	
	// Foreground "Sprite" rendering ================================
	// The OAM is an additional memory internal to the PPU. It is
	// not connected via the bus. It stores the locations of
	// 64 8x8 (or 8x16) tiles to be drawn on the next frame.
	private static class ObjectAttributeEntry
	{
		/*unsigned 8bit*/ int y;			// Y position of sprite
		/*unsigned 8bit*/ int id;			// ID of tile from pattern memory
		/*unsigned 8bit*/ int attribute;	// Flags define how sprite should be rendered
		/*unsigned 8bit*/ int x;			// X position of sprite
		boolean draw = false;
		int count_down;
		
		public void clear()
		{
			y = 0xFF;
			id = 0xFF;
			attribute = 0xFF;
			x = 0xFF;
			draw = false;
			count_down = 8;
		}
	}
	
	// A convenience class to keep information about a frame.
	public static class Frame
	{
		public BufferedImage frame;
		public int[] screen_data;
		public int frame_number;
	}
}
