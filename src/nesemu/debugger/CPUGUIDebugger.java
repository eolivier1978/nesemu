package nesemu.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JLabel;

import nesemu.engine.Launcher;
import nesemu.hardware.cpu.MOS6502;
import nesemu.util.Convert;

public class CPUGUIDebugger extends GUIDebugger
{
	public JLabel label_cpu_status_n;
	public JLabel label_cpu_status_v;
	public JLabel label_cpu_status_u;
	public JLabel label_cpu_status_b;
	public JLabel label_cpu_status_d;
	public JLabel label_cpu_status_i;
	public JLabel label_cpu_status_z;
	public JLabel label_cpu_status_c;
	public JLabel label_cpu_program_counter;
	public JLabel label_cpu_accumulator;
	public JLabel label_cpu_register_x;
	public JLabel label_cpu_register_y;
	public JLabel label_cpu_stack_pointer;
	public JLabel[] label_memory_location_zero_page;
	public JLabel[] label_memory_location_program_page;
	public JLabel[] label_instructions_before;
	public JLabel label_current_instruction;
	public JLabel[] label_instructions_after;
	
	public CPUGUIDebugger(Launcher nes_emu_runner)
	{
		super(nes_emu_runner);
		nes_emu_runner.addDebugger(this);
	}
	
	public void openDebuggerWindow()
	{
		super.openDebuggerWindow();
		
		label_memory_location_zero_page = new JLabel[16];
		label_memory_location_program_page = new JLabel[16];
		label_instructions_before = new JLabel[12];
		label_instructions_after = new JLabel[13];
		
		debug_frame = new JFrame("CPU Debugger");
		debug_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		debug_frame.setLocation(275, 14);
		
		debug_frame.add(debug_panel, BorderLayout.CENTER);
		
		for (int n=0; n < 16; n++)
		{
			String line = "";
			for (int j=0; j < 16; j++)
			{
				line += "00 ";
			}
			line = line.trim();
			String start_location = "$"+Convert.getHexStringFromUnsigned16BitInt(n * 16)+": ";
			label_memory_location_zero_page[n] = addLabel(start_location+line, 0, n * font_size);
		}
		
		int y_offset = font_size * 16 + font_size;
		for (int n=0; n < 16; n++)
		{
			String line = "";
			for (int j=0; j < 16; j++)
			{
				line += "00 ";
			}
			line = line.trim();
			String start_location = "$"+Convert.getHexStringFromUnsigned16BitInt(n * 16)+": ";
			label_memory_location_program_page[n] = addLabel(start_location+line, 0, n * font_size + y_offset);
		}
		
		int x_offset = label_memory_location_zero_page[0].getWidth();
		JLabel status_label = addLabel("STATUS:", x_offset, 0);
		
		int status_offset = x_offset + status_label.getWidth() + font_size/2;
		label_cpu_status_n = addLabel("N", status_offset, 0, Color.RED);
		label_cpu_status_v = addLabel("V", (int)Math.round(status_offset + 1*font_size*1.25), 0, Color.RED);
		label_cpu_status_u = addLabel("U", (int)Math.round(status_offset + 2*font_size*1.25), 0, Color.RED);
		label_cpu_status_b = addLabel("B", (int)Math.round(status_offset + 3*font_size*1.25), 0, Color.RED);
		label_cpu_status_d = addLabel("D", (int)Math.round(status_offset + 4*font_size*1.25), 0, Color.RED);
		label_cpu_status_i = addLabel("I", (int)Math.round(status_offset + 5*font_size*1.25), 0, Color.RED);
		label_cpu_status_z = addLabel("Z", (int)Math.round(status_offset + 6*font_size*1.25), 0, Color.RED);
		label_cpu_status_c = addLabel("C", (int)Math.round(status_offset + 7*font_size*1.25), 0, Color.RED);
		label_cpu_program_counter = addLabel("PC: $0000", x_offset, font_size);
		label_cpu_accumulator = addLabel("A:  $00  [0]", x_offset, font_size*2);
		label_cpu_register_x = addLabel("X:  $00  [0]", x_offset, font_size*3);
		label_cpu_register_y = addLabel("Y:  $00  [0]", x_offset, font_size*4);
		label_cpu_stack_pointer = addLabel("Stack P: $0000", x_offset, font_size*5);
		
		int instructions_offset = font_size*7;
		for (int n=0; n < 26; n++)
		{
			if (n < 12)
			{
				label_instructions_before[n] = addLabel("Instruction:                                       ", x_offset, instructions_offset + (n*font_size));
			}
			else if (n == 12)
			{
				label_current_instruction = addLabel("Instruction:                                       ", x_offset, instructions_offset + (n*font_size), Color.CYAN);
			}
			else
			{
				label_instructions_after[n - 13] = addLabel("Instruction:                                       ", x_offset, instructions_offset + (n*font_size));
			}
		}
		
		JLabel instruction_label = addLabel("SPACE = Toggle Run            C = Step Instruction             F = Next Frame", font_size*2, instructions_offset+font_size*27);
		addLabel("R = RESET                     I = IRQ  N = NMI                 Q=QUIT", font_size*2, instructions_offset+font_size*28);
		
		debug_panel.setPreferredSize(
			new Dimension(
				(int)Math.round(instruction_label.getWidth()*1.2),
				instructions_offset+font_size*30));
		debug_frame.pack();
		debug_frame.setVisible(true);
	}
	
	public void displayCPUStatusGUI()
	{
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.N) == 0)
		{
			label_cpu_status_n.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_n.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.V) == 0)
		{
			label_cpu_status_v.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_v.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.U) == 0)
		{
			label_cpu_status_u.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_u.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.B) == 0)
		{
			label_cpu_status_b.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_b.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.D) == 0)
		{
			label_cpu_status_d.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_d.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.I) == 0)
		{
			label_cpu_status_i.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_i.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.Z) == 0)
		{
			label_cpu_status_z.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_z.setForeground(Color.GREEN);
		}
		
		if (nes.cpu.GetFlag(MOS6502.FLAGS6502.C) == 0)
		{
			label_cpu_status_c.setForeground(Color.RED);
		}
		else
		{
			label_cpu_status_c.setForeground(Color.GREEN);
		}
		
		label_cpu_program_counter.setText(
			"PC: $"+ Convert.getHexStringFromUnsigned16BitInt(nes.cpu.pc));
		label_cpu_accumulator.setText("A: $"+ Convert.getHexStringFromByte((byte)nes.cpu.a)+"  ["+nes.cpu.a+"]");
		label_cpu_register_x.setText("X: $"+ Convert.getHexStringFromByte((byte)nes.cpu.x)+"  ["+nes.cpu.x+"]");
		label_cpu_register_y.setText("Y: $"+ Convert.getHexStringFromByte((byte)nes.cpu.y)+"  ["+nes.cpu.y+"]");
		label_cpu_stack_pointer.setText("Stack P: $"+ Convert.getHexStringFromByte((byte)nes.cpu.stkp));
	}
	
	public void displayDisassembledInstructions()
	{
		if ((nes.is_powered_on)&&(label_current_instruction != null))
		{
			int disassembled_instruction_index = getDisassembledInstructionIndex(nes.cpu.pc);
			label_current_instruction.setText(disassembleInstructionByIndex(disassembled_instruction_index));
			label_instructions_before[0].setText(disassembleInstructionByIndex(disassembled_instruction_index - 12));
			label_instructions_before[1].setText(disassembleInstructionByIndex(disassembled_instruction_index - 11));
			label_instructions_before[2].setText(disassembleInstructionByIndex(disassembled_instruction_index - 10));
			label_instructions_before[3].setText(disassembleInstructionByIndex(disassembled_instruction_index - 9));
			label_instructions_before[4].setText(disassembleInstructionByIndex(disassembled_instruction_index - 8));
			label_instructions_before[5].setText(disassembleInstructionByIndex(disassembled_instruction_index - 7));
			label_instructions_before[6].setText(disassembleInstructionByIndex(disassembled_instruction_index - 6));
			label_instructions_before[7].setText(disassembleInstructionByIndex(disassembled_instruction_index - 5));
			label_instructions_before[8].setText(disassembleInstructionByIndex(disassembled_instruction_index - 4));
			label_instructions_before[9].setText(disassembleInstructionByIndex(disassembled_instruction_index - 3));
			label_instructions_before[10].setText(disassembleInstructionByIndex(disassembled_instruction_index - 2));
			label_instructions_before[11].setText(disassembleInstructionByIndex(disassembled_instruction_index - 1));
			label_instructions_after[0].setText(disassembleInstructionByIndex(disassembled_instruction_index + 1));
			label_instructions_after[1].setText(disassembleInstructionByIndex(disassembled_instruction_index + 2));
			label_instructions_after[2].setText(disassembleInstructionByIndex(disassembled_instruction_index + 3));
			label_instructions_after[3].setText(disassembleInstructionByIndex(disassembled_instruction_index + 4));
			label_instructions_after[4].setText(disassembleInstructionByIndex(disassembled_instruction_index + 5));
			label_instructions_after[5].setText(disassembleInstructionByIndex(disassembled_instruction_index + 6));
			label_instructions_after[6].setText(disassembleInstructionByIndex(disassembled_instruction_index + 7));
			label_instructions_after[7].setText(disassembleInstructionByIndex(disassembled_instruction_index + 8));
			label_instructions_after[8].setText(disassembleInstructionByIndex(disassembled_instruction_index + 9));
			label_instructions_after[9].setText(disassembleInstructionByIndex(disassembled_instruction_index + 10));
			label_instructions_after[10].setText(disassembleInstructionByIndex(disassembled_instruction_index + 11));
			label_instructions_after[11].setText(disassembleInstructionByIndex(disassembled_instruction_index + 12));
			label_instructions_after[12].setText(disassembleInstructionByIndex(disassembled_instruction_index + 13));
		}
	}

	public void displayMemoryAtLocationGUI(int location)
	{
		for (int row=0; row < 16; row++)
		{
			String row_string = "$"+Convert.getHexStringFromUnsigned16BitInt(location + row * 16)+": ";
			for (int column=0; column < 16; column++)
			{
				byte byte_to_display = (byte)nes.cpuRead(location + row * 16 + column);
				String byte_hex_string = Convert.getHexStringFromByte(byte_to_display);
				row_string += byte_hex_string + " ";
			}
			label_memory_location_program_page[row].setText(row_string);
		}
	}
	
	public void displayMemoryAtZeroPage()
	{
		for (int row=0; row < 16; row++)
		{
			String row_string = "$"+Convert.getHexStringFromUnsigned16BitInt(row * 16)+": ";
			for (int column=0; column < 16; column++)
			{
				byte byte_to_display = (byte)nes.cpuRead(row * 16 + column);
				String byte_hex_string = Convert.getHexStringFromByte(byte_to_display);
				row_string += byte_hex_string + " ";
			}
			label_memory_location_zero_page[row].setText(row_string);
		}
	}
	
	public void updateDebugger()
	{
		displayCPUStatusGUI();
		displayMemoryAtZeroPage();
		displayMemoryAtLocationGUI((nes.cpu.pc / 16) * 16);
		displayDisassembledInstructions();
	}
	
	public void NESEmuRunnerFrameDrawn()
	{
		if (shouldUpdateDebugger())
		{
			updateDebugger();
		}
	}
	
	public void NESEmuRunnerCPUInstructionExecuted()
	{
		
	}
	
	public void NESEmuRunnerCPUInstructionStepped()
	{
		updateDebugger();
	}
	
	public void NESEmuRunnerFrameStepped()
	{
		updateDebugger();
	}
	
	public void debugInGUI()
	{
		super.debugInGUI();
		debug_frame.addKeyListener(new KeyListener() 
		{
	        @Override
	        public void keyTyped(KeyEvent e) 
	        {
	        }

	        @Override
	        public void keyPressed(KeyEvent e) 
	        {
	        	
	        }

	        @Override
	        public void keyReleased(KeyEvent e) 
	        {
	        	key_pressed = e.getKeyCode();
	        	
	        	if ((key_pressed == (char)'q') || (key_pressed == (char)'Q'))
	        	{
	        		System.exit(0);
	        	}
	        	else
	        	if ((key_pressed == (char)'r') || (key_pressed == (char)'R'))
	        	{
	        		nes_emu_runner.reset();
	        	}
	        	else
        		if ((key_pressed == (char)'c') || (key_pressed == (char)'C'))
	        	{
        			nes_emu_runner.stepCPUInstruction();
	        	}
        		else
        		if ((key_pressed == (char)'f') || (key_pressed == (char)'F'))
	        	{
        			nes_emu_runner.stepFrame();
	        	}
        		else
        		if (key_pressed == 32)
	        	{
        			nes_emu_runner.toggleExecution();
	        	}
	        }
	    });
	}
}
