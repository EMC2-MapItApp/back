package emc.mapIt.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Cubre {@link ClientIpResolver}: debe confiar en el último salto de {@code X-Forwarded-For}
 * (el que añade la GFE de Cloud Run), nunca en el primero (el que fija el propio cliente).
 */
class ClientIpResolverTest {

    private HttpServletRequest request(String xff, String xRealIp, String remoteAddr) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        lenient().when(req.getHeader("X-Real-IP")).thenReturn(xRealIp);
        lenient().when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    @Test
    void xffConVariosSaltos_devuelveElUltimo() {
        String ip = ClientIpResolver.resolve(request("1.1.1.1, 2.2.2.2, 3.3.3.3", null, "9.9.9.9"));

        assertThat(ip).isEqualTo("3.3.3.3");
    }

    @Test
    void xffConUnSoloValor_devuelveEseValor() {
        String ip = ClientIpResolver.resolve(request("4.4.4.4", null, "9.9.9.9"));

        assertThat(ip).isEqualTo("4.4.4.4");
    }

    @Test
    void sinXff_conXRealIp_devuelveXRealIp() {
        String ip = ClientIpResolver.resolve(request(null, "5.5.5.5", "9.9.9.9"));

        assertThat(ip).isEqualTo("5.5.5.5");
    }

    @Test
    void sinNingunaCabecera_devuelveRemoteAddr() {
        String ip = ClientIpResolver.resolve(request(null, null, "6.6.6.6"));

        assertThat(ip).isEqualTo("6.6.6.6");
    }

    @Test
    void xffEnBlanco_caeAXRealIp() {
        String ip = ClientIpResolver.resolve(request("   ", "7.7.7.7", "9.9.9.9"));

        assertThat(ip).isEqualTo("7.7.7.7");
    }

    @Test
    void xffConUltimoSaltoEnBlanco_ignoraLosVaciosYDevuelveElAnterior() {
        String ip = ClientIpResolver.resolve(request("1.1.1.1, 2.2.2.2, ", null, "9.9.9.9"));

        assertThat(ip).isEqualTo("2.2.2.2");
    }

    @Test
    void sinNadaUtilizable_devuelveLoopbackPorDefecto() {
        String ip = ClientIpResolver.resolve(request(null, null, null));

        assertThat(ip).isEqualTo("127.0.0.1");
    }
}
