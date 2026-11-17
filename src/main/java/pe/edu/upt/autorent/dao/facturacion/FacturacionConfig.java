package pe.edu.upt.autorent.dao.facturacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.edu.upt.autorent.repositorio.FacturacionElectronicaPort;

/**
 * Elige el adaptador de facturación electrónica:
 * si hay RUTA y TOKEN de Nubefact usa Nubefact; si no, usa la emisión de prueba interna (respaldo).
 */
@Configuration
public class FacturacionConfig {
    private static final Logger log = LoggerFactory.getLogger(FacturacionConfig.class);

    @Bean
    public FacturacionElectronicaPort facturacionElectronica(
            @Value("${autorent.nubefact.ruta:}") String ruta,
            @Value("${autorent.nubefact.token:}") String token,
            @Value("${autorent.nubefact.serie-boleta:BBB1}") String serieBoletaNubefact,
            @Value("${autorent.nubefact.serie-nota-credito:BBB1}") String serieNotaNubefact,
            @Value("${autorent.empresa.ruc:20123456786}") String ruc,
            @Value("${autorent.comprobantes.serie-boleta:B001}") String serieBoleta,
            @Value("${autorent.comprobantes.serie-nota-credito:BC01}") String serieNota) {
        if (ruta != null && !ruta.isBlank() && token != null && !token.isBlank()) {
            log.info("Facturación electrónica: Nubefact ({}).", ruta.replaceAll("/api/v1/.*", "/api/v1/..."));
            return new NubefactAdapter(ruta, token, serieBoletaNubefact, serieNotaNubefact);
        }
        log.info("Facturación electrónica: modo de prueba interno (configure autorent.nubefact.ruta y token para enviar a SUNAT).");
        return new FacturacionSimuladaAdapter(ruc, serieBoleta, serieNota);
    }
}
