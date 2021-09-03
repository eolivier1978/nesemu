package nesemu.hardware.mapper;

public class Mapper_000 extends AMapper
{	
	public Mapper_000(/*unsigned 8bit*/ int prgBanks, /*unsigned 8bit*/ int chrBanks)
	{
		super(prgBanks, chrBanks);
	}
	
	public boolean cpuMapRead(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int[] data_by_ref)
	{
		// if PRGROM is 16KB
		//     CPU Address Bus          PRG ROM
		//     0x8000 -> 0xBFFF: Map    0x0000 -> 0x3FFF
		//     0xC000 -> 0xFFFF: Mirror 0x0000 -> 0x3FFF
		// if PRGROM is 32KB
		//     CPU Address Bus          PRG ROM
		//     0x8000 -> 0xFFFF: Map    0x0000 -> 0x7FFF	
		mapped_addr_by_ref[0] = addr;
		if (addr >= 0x8000 & addr <= 0xFFFF)
		{
			mapped_addr_by_ref[0] = addr & (nPRGBanks > 1 ? (int)0x7FFF : (int)0x3FFF);
			return true;
		}
		return false;
	}
	
	public boolean cpuMapWrite(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int data)
	{
		mapped_addr_by_ref[0] = addr;
		if (addr >= 0x8000 & addr <= 0xFFFF)
		{
			mapped_addr_by_ref[0] = addr & (nPRGBanks > 1 ? (int)0x7FFF : (int)0x3FFF);
			return true;
		}
		return false;
	}
	
	public boolean ppuMapRead(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref)
	{
		// There is no mapping required for PPU
		// PPU Address Bus          CHR ROM
		// 0x0000 -> 0x1FFF: Map    0x0000 -> 0x1FFF
		mapped_addr_by_ref[0] = addr;
		if (addr >= 0x0000 & addr <= 0x1FFF)
		{
			return true;
		}
		return false;
	}
	
	public boolean ppuMapWrite(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref)
	{
		mapped_addr_by_ref[0] = addr;
		if (addr >= 0x0000 && addr <= 0x1FFF)
		{
			if (nCHRBanks == 0)
			{
				// Treat as RAM
				return true;
			}
		}
		return false;
	}
}
