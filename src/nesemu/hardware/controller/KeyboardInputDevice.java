package nesemu.hardware.controller;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyboardInputDevice extends AInputDevice implements KeyListener
{	
	private boolean[] keys_down_controller1;
	private boolean[] keys_down_controller2;
	
	public KeyboardInputDevice()
	{
		reset();
	}
	
	public int getController1Input()
	{
		// Handle input for controller in port #1
		int controller_1_state = 0x00;
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.A_BUTTON] ? 0x80 : 0x00;     // A Button
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.B_BUTTON] ? 0x40 : 0x00;     // B Button
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.SELECT] ? 0x20 : 0x00;     // Select
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.START] ? 0x10 : 0x00;     // Start
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.UP] ? 0x08 : 0x00;
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.DOWN] ? 0x04 : 0x00;
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.LEFT] ? 0x02 : 0x00;
		controller_1_state |= keys_down_controller1[KeyboardInputDevice.RIGHT] ? 0x01 : 0x00;
		
		return controller_1_state;
	}
	
	public int getController2Input()
	{
		// Handle input for controller in port #1
		int controller_2_state = 0x00;
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.A_BUTTON] ? 0x80 : 0x00;     // A Button
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.B_BUTTON] ? 0x40 : 0x00;     // B Button
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.SELECT] ? 0x20 : 0x00;     // Select
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.START] ? 0x10 : 0x00;     // Start
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.UP] ? 0x08 : 0x00;
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.DOWN] ? 0x04 : 0x00;
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.LEFT] ? 0x02 : 0x00;
		controller_2_state |= keys_down_controller2[KeyboardInputDevice.RIGHT] ? 0x01 : 0x00;
		
		return controller_2_state;
	}
	
	@Override
    public void keyTyped(KeyEvent e) 
    {
    }

    @Override
    public void keyPressed(KeyEvent e) 
    {
    	int key_pressed = e.getKeyCode();
    	
    	if ((key_pressed == (char)'.') || (key_pressed == (char)'>'))
    	{
    		keys_down_controller1[A_BUTTON] = true;
    	}
    	if ((key_pressed == (char)',') || (key_pressed == (char)'<'))
    	{
    		keys_down_controller1[B_BUTTON] = true;
    	}
    	if ((key_pressed == (char)'k') || (key_pressed == (char)'K'))
    	{
    		keys_down_controller1[SELECT] = true;
    	}
    	if ((key_pressed == (char)'l') || (key_pressed == (char)'L'))
    	{
    		keys_down_controller1[START] = true;
    	}
    	if (key_pressed == 38) // UP
    	{
    		keys_down_controller1[UP] = true;
    	}
    	if (key_pressed == 40) // DOWN
    	{
    		keys_down_controller1[DOWN] = true;
    	}
    	if (key_pressed == 37) // LEFT
    	{
    		keys_down_controller1[LEFT] = true;
    	}
    	if (key_pressed == 39) // RIGHT
    	{
    		keys_down_controller1[RIGHT] = true;
    	}
    	
    	if ((key_pressed == (char)'s') || (key_pressed == (char)'S'))
    	{
    		keys_down_controller2[A_BUTTON] = true;
    	}
    	if ((key_pressed == (char)'a') || (key_pressed == (char)'A'))
    	{
    		keys_down_controller2[B_BUTTON] = true;
    	}
    	if ((key_pressed == (char)'q') || (key_pressed == (char)'Q'))
    	{
    		keys_down_controller2[SELECT] = true;
    	}
    	if ((key_pressed == (char)'w') || (key_pressed == (char)'W'))
    	{
    		keys_down_controller2[START] = true;
    	}
    	if ((key_pressed == (char)'t') || (key_pressed == (char)'T')) // UP
    	{
    		keys_down_controller2[UP] = true;
    	}
    	if ((key_pressed == (char)'g') || (key_pressed == (char)'G')) // DOWN
    	{
    		keys_down_controller2[DOWN] = true;
    	}
    	if ((key_pressed == (char)'f') || (key_pressed == (char)'F')) // LEFT
    	{
    		keys_down_controller2[LEFT] = true;
    	}
    	if ((key_pressed == (char)'h') || (key_pressed == (char)'H')) // RIGHT
    	{
    		keys_down_controller2[RIGHT] = true;
    	}
    }

    @Override
    public void keyReleased(KeyEvent e) 
    {
    	int key_pressed = e.getKeyCode();
    	
    	if ((key_pressed == (char)'.') || (key_pressed == (char)'>'))
    	{
    		keys_down_controller1[A_BUTTON] = false;
    	}
    	if ((key_pressed == (char)',') || (key_pressed == (char)'<'))
    	{
    		keys_down_controller1[B_BUTTON] = false;
    	}
    	if ((key_pressed == (char)'k') || (key_pressed == (char)'K'))
    	{
    		keys_down_controller1[SELECT] = false;
    	}
    	if ((key_pressed == (char)'l') || (key_pressed == (char)'L'))
    	{
    		keys_down_controller1[START] = false;
    	}
    	if (key_pressed == 38) // UP
    	{
    		keys_down_controller1[UP] = false;
    	}
    	if (key_pressed == 40) // DOWN
    	{
    		keys_down_controller1[DOWN] = false;
    	}
    	if (key_pressed == 37) // LEFT
    	{
    		keys_down_controller1[LEFT] = false;
    	}
    	if (key_pressed == 39) // RIGHT
    	{
    		keys_down_controller1[RIGHT] = false;
    	}
    	
    	if ((key_pressed == (char)'s') || (key_pressed == (char)'S'))
    	{
    		keys_down_controller2[A_BUTTON] = false;
    	}
    	if ((key_pressed == (char)'a') || (key_pressed == (char)'A'))
    	{
    		keys_down_controller2[B_BUTTON] = false;
    	}
    	if ((key_pressed == (char)'q') || (key_pressed == (char)'Q'))
    	{
    		keys_down_controller2[SELECT] = false;
    	}
    	if ((key_pressed == (char)'w') || (key_pressed == (char)'W'))
    	{
    		keys_down_controller2[START] = false;
    	}
    	if ((key_pressed == (char)'t') || (key_pressed == (char)'T')) // UP
    	{
    		keys_down_controller2[UP] = false;
    	}
    	if ((key_pressed == (char)'g') || (key_pressed == (char)'G')) // DOWN
    	{
    		keys_down_controller2[DOWN] = false;
    	}
    	if ((key_pressed == (char)'f') || (key_pressed == (char)'F')) // LEFT
    	{
    		keys_down_controller2[LEFT] = false;
    	}
    	if ((key_pressed == (char)'h') || (key_pressed == (char)'H')) // RIGHT
    	{
    		keys_down_controller2[RIGHT] = false;
    	}
    }
    
    public void reset()
    {
    	keys_down_controller1 = new boolean[8];
		// Order: A button, B button, Select, Start, up, down, left, right
		for (int n=0; n < 8; n++)
		{
			keys_down_controller1[n] = false;
		}
		
		keys_down_controller2 = new boolean[8];
		// Order: A button, B button, Select, Start, up, down, left, right
		for (int n=0; n < 8; n++)
		{
			keys_down_controller2[n] = false;
		}
    }
    
    public static final int A_BUTTON = 0;
    public static final int B_BUTTON = 1;
    public static final int SELECT = 2;
    public static final int START = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;
    public static final int LEFT = 6;
    public static final int RIGHT = 7;
}
