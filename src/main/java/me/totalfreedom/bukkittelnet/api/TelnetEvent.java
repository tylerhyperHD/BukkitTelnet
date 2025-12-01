package me.totalfreedom.bukkittelnet.api;

import org.bukkit.event.server.ServerEvent;

public abstract class TelnetEvent extends ServerEvent
{

	public TelnetEvent()
	{
		super();
	}

	public TelnetEvent(boolean isAsync)
	{
		super(isAsync);
	}

}
