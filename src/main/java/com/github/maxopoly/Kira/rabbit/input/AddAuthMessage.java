package com.github.maxopoly.Kira.rabbit.input;

import java.util.UUID;

import org.json.JSONObject;

import com.github.maxopoly.Kira.KiraMain;

public class AddAuthMessage extends RabbitMessage {

	public AddAuthMessage() {
		super("addauth");
	}

	@Override
	public void handle(JSONObject json) {
		UUID uuid = UUID.fromString(json.getString("uuid"));
		String code = json.getString("code");
		String name = json.getString("name");
		KiraMain.getInstance().getAuthManager().putCode(uuid, name, code);
	}

}