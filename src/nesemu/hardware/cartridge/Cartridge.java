package nesemu.hardware.cartridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nesemu.hardware.mapper.AMapper.MIRROR;
import nesemu.hardware.mapper.Mapper_000;
import nesemu.hardware.mapper.Mapper_002;

public class Cartridge extends ACartridge
{	
	private /*unsigned 8bit*/ int nMapperID = 0;
	private /*unsigned 8bit*/ int nPRGBanks = 0;
	private /*unsigned 8bit*/ int nCHRBanks = 0;
	
	private sHeader header = new sHeader();
	public MIRROR hw_mirror;

	int[] mapped_addr_by_ref = new int[1];
	
	// Constructs and loads a cartridge from a file.
	public Cartridge(String sFileName) throws IOException
	{
		super(sFileName);
		byte[] file_bytes = new byte[10000000];
		File f = new File(sFileName);
		FileInputStream fis = new FileInputStream(f);
		fis.read(file_bytes);
		fis.close();
		
		char[] temp_name = new char[4];
		int file_index = 0;
		
		// Read header
		for (file_index=0; file_index < 16; file_index++)
		{
			if (file_index < 4)
			{
				temp_name[file_index] = (char)file_bytes[file_index];
			}
			else if (file_index == 4)
			{
				header.prg_rom_chunks = file_bytes[file_index];
			}
			else if (file_index == 5)
			{
				header.chr_rom_chunks = file_bytes[file_index];
			}
			else if (file_index == 6)
			{
				header.mapper1 = file_bytes[file_index];
			}
			else if (file_index == 7)
			{
				header.mapper2 = file_bytes[file_index];
			}
			else if (file_index == 8)
			{
				header.prg_ram_size = file_bytes[file_index];
			}
			else if (file_index == 9)
			{
				header.tv_system1 = file_bytes[file_index];
			}
			else if (file_index == 10)
			{
				header.tv_system2 = file_bytes[file_index];
			}
			else if (file_index < 15)
			{
				header.unused[file_index - 11] = (char)file_bytes[file_index];
			}
		}
		header.name = new String(temp_name);
		
		if ((header.mapper1 & 0x04) != 0)
		{
			file_index += 512;
		}
		
		// Determine Mapper ID
		nMapperID = ((header.mapper2 >> 4) << 4) | (header.mapper1 >> 4);
		
		// Determine mirroring mode
		hw_mirror = (header.mapper1 & 0x01) == 1 ? MIRROR.VERTICAL : MIRROR.HORIZONTAL;

		// "Discover" File Format
		/*unsigned 8bit*/ int nFileType = 1;
		
		if (nFileType == 0)
		{
			
		}
		
		if (nFileType == 1)
		{
			nPRGBanks = header.prg_rom_chunks;
			vPRGMemory = new int[nPRGBanks * 16384];
			for (int n = file_index; n < file_index + (nPRGBanks * 16384); n++)
			{
				vPRGMemory[n - file_index] = ((int)file_bytes[n]) & 0xFF;
			}
			file_index += nPRGBanks * 16384;
			
			nCHRBanks = header.chr_rom_chunks;
			int nNumberBytesToRead;
			if (nCHRBanks == 0)
			{
				vCHRMemory = new int[8192];
				nNumberBytesToRead = 8192;
			}
			else
			{
				vCHRMemory = new int[nCHRBanks * 8192];
				nNumberBytesToRead = nCHRBanks * 8192;
				for (int n = file_index; n < file_index + nNumberBytesToRead; n++)
				{
					vCHRMemory[n - file_index] = ((int)file_bytes[n]) & 0xFF;
				}
			}
			file_index += nCHRBanks * 8192;
		}
		
		if (nFileType == 2)
		{
			
		}
		
		// Load appropriate mapper
		switch (nMapperID)
		{
		case 0: mapper = new Mapper_000(nPRGBanks, nCHRBanks);
			break;
		case 2: mapper = new Mapper_002(nPRGBanks, nCHRBanks);
			break;
		}
	}
	
	// Communication with Main Bus
	public boolean cpuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		if (mapper.cpuMapWrite(addr, mapped_addr_by_ref, data))
		{
			vPRGMemory[mapped_addr_by_ref[0]] = data;
			return true;
		}
		return false;
	}

	public boolean cpuRead(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int[] data_by_ref)
	{
		if (mapper.cpuMapRead(addr, mapped_addr_by_ref, data_by_ref))
		{
			data_by_ref[0] = vPRGMemory[mapped_addr_by_ref[0]];
			return true;
		}
		return false;
	}
	
	// Communication with PPU Bus
	public boolean ppuWrite(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		if (mapper.ppuMapWrite(addr, mapped_addr_by_ref))
		{
			vCHRMemory[mapped_addr_by_ref[0]] = data;
			return true;
		}
		return false;
	}
	
	public boolean ppuRead(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int[] data_by_ref)
	{
		if (mapper.ppuMapRead(addr, mapped_addr_by_ref))
		{
			data_by_ref[0] = vCHRMemory[mapped_addr_by_ref[0]];
			return true;
		}
		return false;
	}
	
	public void reset()
	{
		// Note: This does not reset the ROM contents, but does reset the mapper.
		if (mapper != null)
			mapper.reset();
	}
	
	private static class sHeader
	{
		private String name;
		/*unsigned 8bit*/ int prg_rom_chunks;
		/*unsigned 8bit*/ int chr_rom_chunks;
		/*unsigned 8bit*/ int mapper1;
		/*unsigned 8bit*/ int mapper2;
		/*unsigned 8bit*/ int prg_ram_size;
		/*unsigned 8bit*/ int tv_system1;
		/*unsigned 8bit*/ int tv_system2;
		private char[] unused = new char[5];
	}
	
	public MIRROR Mirror()
	{
		MIRROR m = mapper.mirror();
		if (m == MIRROR.HARDWARE)
		{
			// Mirror configuration was defined in hardware via soldering.
			return hw_mirror;
		}
		else
		{
			// Mirror configuration can be dynamically set via mapper.
			return m;
		}
	}
}
