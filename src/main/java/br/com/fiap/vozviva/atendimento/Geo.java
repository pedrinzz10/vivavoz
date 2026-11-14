package br.com.fiap.vozviva.atendimento;

/**
 * Cálculos de distância sobre a superfície da Terra (esfera de raio médio), suficientes para escolher o
 * atendimento mais próximo dentro de poucos quilômetros.
 */
final class Geo {

	static final double RAIO_TERRA_METROS = 6_371_008.8;
	private static final double METROS_POR_GRAU_LATITUDE = Math.PI * RAIO_TERRA_METROS / 180;

	private Geo() {
	}

	/** Distância em linha reta (Haversine), em metros. */
	static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 2 * RAIO_TERRA_METROS * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	/**
	 * Retângulo que contém o círculo de raio {@code raioMetros}: usado como pré-filtro no banco (com índice),
	 * antes do cálculo exato de distância.
	 */
	static Area areaAoRedor(double latitude, double longitude, double raioMetros) {
		double dLat = raioMetros / METROS_POR_GRAU_LATITUDE;
		// Perto dos polos o cosseno tende a zero; o limite evita divisão por zero (e cobre toda a longitude).
		double cos = Math.max(Math.cos(Math.toRadians(latitude)), 0.01);
		double dLon = Math.min(raioMetros / (METROS_POR_GRAU_LATITUDE * cos), 180);
		return new Area(Math.max(latitude - dLat, -90), Math.min(latitude + dLat, 90),
				Math.max(longitude - dLon, -180), Math.min(longitude + dLon, 180));
	}

	record Area(double latitudeMinima, double latitudeMaxima, double longitudeMinima, double longitudeMaxima) {
	}

}
