package nesemu.hardware.cartridge;

import nesemu.hardware.mapper.AMapper;
import nesemu.hardware.mapper.AMapper.MIRROR;

public class ACartridge
{
	protected int[] vPRGMemory = new int[0];
	protected int[] vCHRMemory = new int[0];
	
	protected AMapper mapper;
	
	public ACartridge(String sFileName)
	{
		
	}
	
	// Communication with Main Bus
	public boolean cpuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		return false;
	}
	
	public boolean cpuRead(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int[] data_by_ref)
	{
		return false;
	}
	
	// Communication with PPU Bus
	public boolean ppuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		return false;
	}
	
	public boolean ppuRead(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int[] data_by_ref)
	{
		return false;
	}
	
	public void reset()
	{
	}
	
	public MIRROR Mirror()
	{
		return null;
	}
	
	// Used for debugging.
	public int[] getProgram()
	{
		return vPRGMemory;
	}
	
	public AMapper getMapper()
	{
		return mapper;
	}

}
