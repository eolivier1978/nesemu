package nesemu.hardware.mapper;

public class AMapper
{
	protected /*unsigned 8bit*/ int nPRGBanks = 0;
	protected /*unsigned 8bit*/ int nCHRBanks = 0;
	
	public AMapper(/*unsigned 8bit*/ int prgBanks, /*unsigned 8bit*/ int chrBanks)
	{
		this.nPRGBanks = prgBanks;
		this.nCHRBanks = chrBanks;
	}
	
	// Transform CPU bus address into PRG ROM offset
	public boolean cpuMapRead(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int[] data_by_ref)
	{
		return false;
	}
	
	public boolean cpuMapWrite(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int data)
	{
		return false;
	}
	
	// Transform PPU bus address into CHR ROM offset
	public boolean ppuMapRead(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref)
	{
		return false;
	}
	
	public boolean ppuMapWrite(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref)
	{
		return false;
	}
	
	// Get Mirror mode if mapper is in control
	public MIRROR mirror()
	{
		return MIRROR.HARDWARE;
	}
	
	// IRQ Interface
	public boolean irqState()
	{
		return false;
	}
		
	public boolean irqClear()
	{
		return false;
	}

	// Scanline Counting
	public void scanline()
	{
		
	}
	
	// Reset mapper to known state
	public void reset()
	{
		
	}
	
	public enum MIRROR
	{
		HARDWARE,
		HORIZONTAL,
		VERTICAL,
		ONESCREEN_LO,
		ONESCREEN_HI
	}

}
