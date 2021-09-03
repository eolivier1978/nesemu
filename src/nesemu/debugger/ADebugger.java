package nesemu.debugger;

import java.util.Hashtable;
import java.util.Vector;

import nesemu.engine.Launcher;
import nesemu.hardware.bus.NESBus;
import nesemu.hardware.cpu.MOS6502;
import nesemu.hardware.mapper.AMapper;
import nesemu.util.Convert;

public class ADebugger
{
	public NESBus nes;
	public Launcher nes_emu_runner;
	
	public Vector disassembledInstructions = new Vector();
	public Hashtable disassembledInstructionsIndexes = new Hashtable();
	
	public AMapper mapper;
	
	protected boolean executing = true;
	
	public ADebugger(Launcher nes_emu_runner)
	{
		this.nes_emu_runner = nes_emu_runner;
		this.nes = nes_emu_runner.nes;
		this.executing = nes_emu_runner.isExecuting();
	}
	
	public int getDisassembledInstructionIndex(int instruction_location)
	{
		int[] mapped_addr_by_ref = new int[1];
		int[] data_by_ref = new int[1];
		mapper.cpuMapRead(instruction_location, mapped_addr_by_ref, data_by_ref);
		Integer index = (Integer)disassembledInstructionsIndexes.get(mapped_addr_by_ref[0]);
		if (index == null)
		{
			return -1;
		}
		return index.intValue();
	}
	
	public String disassembleInstruction(int instruction_location)
	{
		int index = getDisassembledInstructionIndex(instruction_location);
		if ((index < 0) || (index > disassembledInstructions.size()))
		{
			return "???";
		}
		DisassembledLineEntry temp = (DisassembledLineEntry)disassembledInstructions.get(index);
		return "$"+ Convert.getHexStringFromUnsigned16BitInt(temp.position) + ": " +
			temp.description;
	}
	
	public String disassembleInstructionByIndex(int index)
	{
		try
		{
		if (index < 0) index = (disassembledInstructions.size() + index) % disassembledInstructions.size();
		if (index > disassembledInstructions.size()) index = index - disassembledInstructions.size();
		
		DisassembledLineEntry temp = (DisassembledLineEntry)disassembledInstructions.get(index);
		return temp.description;
		}
		catch (Exception e)
		{
		return "";
		}
	}
	
	public void disassembleProgram()
	{
		int instruction_location = 0;
		int counter = 0;
		mapper = nes.cartridge.getMapper();
		int[] program = nes.cartridge.getProgram();
		while (instruction_location < program.length)
		{
			int instruction_location_to_store = instruction_location;
			String disassembled_instruction = "";
			disassembled_instruction += "$" + 
				Convert.getHexStringFromUnsigned16BitInt(instruction_location_to_store) + ": ";
			int opcode = program[instruction_location];
			String name = nes.cpu.lookup[opcode].name;
			disassembled_instruction += name;
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ABS)
			{
				if (instruction_location + 2 < program.length)
				{
					byte low_byte = (byte)program[instruction_location + 1];
					byte high_byte = (byte)program[instruction_location + 2];
					disassembled_instruction += " $" + 
						Convert.getHexStringFromByte(high_byte) + Convert.getHexStringFromByte(low_byte);
					disassembled_instruction += " {ABS}";
					instruction_location++;
					instruction_location++;
				}
				else
				{
					instruction_location += 2;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ABX)
			{
				if (instruction_location + 2 < program.length)
				{
					byte low_byte = (byte)program[instruction_location + 1];
					byte high_byte = (byte)program[instruction_location + 2];
					disassembled_instruction += " $" + 
						Convert.getHexStringFromByte(high_byte) + Convert.getHexStringFromByte(low_byte) + " + X";
					disassembled_instruction += " {ABX}";
					instruction_location++;
					instruction_location++;
				}
				else
				{
					instruction_location += 2;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ABY)
			{
				if (instruction_location + 2 < program.length)
				{
					byte low_byte = (byte)program[instruction_location + 1];
					byte high_byte = (byte)program[instruction_location + 2];
					disassembled_instruction += " $" + 
						Convert.getHexStringFromByte(high_byte) + Convert.getHexStringFromByte(low_byte) + " + Y";
					disassembled_instruction += " {ABY}";
					instruction_location++;
					instruction_location++;
				}
				else
				{
					instruction_location += 2;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.IMM)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " #$" + 
						Convert.getHexStringFromByte(immediate_byte);
					disassembled_instruction += " {IMM}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.IMP)
			{
				disassembled_instruction += " {IMP}";
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.IND)
			{
				if (instruction_location + 1 < program.length)
				{
					byte low_byte = (byte)program[instruction_location + 1];
					byte high_byte = (byte)program[instruction_location + 2];
					disassembled_instruction += " &[$" + 
						Convert.getHexStringFromByte(high_byte) + Convert.getHexStringFromByte(low_byte) + "]";
					disassembled_instruction += " {IND}";
					instruction_location++;
					instruction_location++;
				}
				else
				{
					instruction_location += 2;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.IZX)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " &[$00" + 
							Convert.getHexStringFromByte(immediate_byte) + " + " +
							Convert.getHexStringFromByte((byte)nes.cpu.x) + "]";
					disassembled_instruction += " {IZX}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.IZY)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " &[$00" + 
							Convert.getHexStringFromByte(immediate_byte) + " + " +
							Convert.getHexStringFromByte((byte)nes.cpu.y) + "]";
					disassembled_instruction += " {IZY}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.REL)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " [$00" + 
						Convert.getHexStringFromByte(immediate_byte) + " + $" + 
						Convert.getHexStringFromUnsigned16BitInt(nes.cpu.pc) + " + 2]";
					disassembled_instruction += " {REL}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ZP0)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " [$00" + 
						Convert.getHexStringFromByte(immediate_byte) + "]";
					disassembled_instruction += " {ZP0}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ZPX)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " [$00" + 
						Convert.getHexStringFromByte(immediate_byte) + " + " +
						Convert.getHexStringFromByte((byte)nes.cpu.x) + "]";
					disassembled_instruction += " {ZPX}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			if (nes.cpu.lookup[opcode].addrmode instanceof MOS6502.ZPY)
			{
				if (instruction_location + 1 < program.length)
				{
					byte immediate_byte = (byte)program[instruction_location + 1];
					disassembled_instruction += " [$00" + 
						Convert.getHexStringFromByte(immediate_byte) + " + " +
						Convert.getHexStringFromByte((byte)nes.cpu.y) + "]";
					disassembled_instruction += " {ZPX}";
					instruction_location++;
				}
				else
				{
					instruction_location += 1;
				}
			}
			else
			{
				instruction_location += 1;
			}
			instruction_location++;
			
			disassembledInstructions.add(
				new ADebugger.DisassembledLineEntry(instruction_location_to_store, disassembled_instruction));
			disassembledInstructionsIndexes.put(instruction_location_to_store, Integer.valueOf(counter));
			
			counter++;
		}
		System.out.println();
	}
	
	public void NESEmuRunnerFrameDrawn()
	{
		
	}
	
	public void NESEmuRunnerCPUInstructionExecuted()
	{
		
	}
	
	public void NESEmuRunnerCPUInstructionStepped()
	{
		
	}
	
	public void NESEmuRunnerFrameStepped()
	{
		
	}
	
	private static class DisassembledLineEntry
	{
		int position;
		String description;
		
		public DisassembledLineEntry(int position, String description)
		{
			this.position = position;
			this.description = description;
		}
	}

}
