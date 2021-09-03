package nesemu.hardware.cartridge;

import nesemu.hardware.mapper.Mapper_000;

public class NoCartridge extends ACartridge
{
	public NoCartridge(String sFileName)
	{
		super(sFileName);
		mapper = new Mapper_000(0, 0);
	}
}
