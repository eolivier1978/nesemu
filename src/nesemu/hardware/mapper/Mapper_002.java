package nesemu.hardware.mapper;

public class Mapper_002 extends AMapper
{
	private /*unsigned 8bit*/ int nPRGBankSelectLo = 0x00;
	private /*unsigned 8bit*/ int nPRGBankSelectHi = 0x00;
	
	public Mapper_002(/*unsigned 8bit*/ int prgBanks, /*unsigned 8bit*/ int chrBanks)
	{
		super(prgBanks, chrBanks);
	}
	
	public boolean cpuMapRead(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int[] data_by_ref)
	{
		mapped_addr_by_ref[0] = addr;
		if (addr >= 0x8000 && addr <= 0xBFFF)
		{
			mapped_addr_by_ref[0] = (nPRGBankSelectLo << 14) + (addr & 0x3FFF);
			return true;
		}
		else
		if (addr >= 0xC000 && addr <= 0xFFFF)
		{
			mapped_addr_by_ref[0] = (nPRGBankSelectHi << 14) + (addr & 0x3FFF);
			return true;
		}
		return false;
	}
	
	public boolean cpuMapWrite(/*unsigned 16bit*/ int addr, int[] mapped_addr_by_ref, int data)
	{
		mapped_addr_by_ref[0] = addr;
		// Mapper has handled write, but do not update ROMs.
		if (addr >= 0x8000 && addr <= 0xFFFF)
		{		
			nPRGBankSelectLo = data & 0x0F;
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
	
	public void reset()
	{
		nPRGBankSelectLo = 0;
		nPRGBankSelectHi = nPRGBanks - 1;
	}

}
