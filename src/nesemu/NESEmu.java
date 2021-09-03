package nesemu;

import nesemu.engine.Launcher;
import nesemu.hardware.bus.NESBus;

public class NESEmu
{
	// The class that contains the actual NES emulator.
	public NESBus nes;
	
	public NESEmu()
	{
		runEmulator();
	}
	
	// Loads the selected ROM and runs the emulator
	public void runEmulator()
	{
		try
		{
			nes = new NESBus();		
			Launcher nes_emu_runner = new Launcher(nes, false);
			nes_emu_runner.run();
		}
		catch (Exception e)
		{
			System.out.println("The emulation could not be started. The error reported is:");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void main(String args[])
	{
		new NESEmu();
	}
}
