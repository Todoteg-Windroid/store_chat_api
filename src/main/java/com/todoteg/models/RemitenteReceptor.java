package com.todoteg.models;

import org.springframework.stereotype.Component;

@Component
public class RemitenteReceptor {
	private String remitente;
	private String receptor;
	public String getRemitente() {
		return remitente;
	}
	public void setRemitente(String remitente) {
		this.remitente = remitente;
	}
	public String getReceptor() {
		return receptor;
	}
	public void setReceptor(String receptor) {
		this.receptor = receptor;
	}
	

}
