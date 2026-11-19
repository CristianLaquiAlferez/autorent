/*
 * Validaciones del navegador comunes a todas las pantallas de AutoRent.
 *
 * El servidor SIEMPRE vuelve a validar todo (es la regla real). Este script solo evita que el usuario
 * llegue a enviar datos que el servidor rechazaría, y le avisa en el mismo momento en que se equivoca.
 *
 * - Fechas: respeta los atributos min y max. Safari en iPhone no los aplica en su calendario,
 *   por eso aquí se revisa cada fecha al elegirla y se muestra el aviso.
 * - Fechas enlazadas: un campo con data-despues-de="idDelInicio" ajusta su mínimo y máximo según
 *   la fecha de inicio (data-dias-min, data-dias-max). Ej.: devolución entre 1 y 30 días después del recojo.
 * - Números: muestra el aviso de mínimo/máximo al salir del campo, no recién al enviar.
 * - Filtros al escribir: los campos numéricos (DNI, CVV, tarjeta) solo aceptan dígitos; el número de
 *   tarjeta se agrupa de 4 en 4 (data-agrupar); el vencimiento se escribe MM/AA (data-formato="mmaa");
 *   nombres, apellidos y titular solo aceptan letras, espacio, apóstrofo y guion.
 */
(function () {
    const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
        'septiembre', 'octubre', 'noviembre', 'diciembre'];

    /** "2026-11-20" -> "20 de noviembre de 2026" */
    function legible(iso) {
        const [a, m, d] = iso.split('-').map(Number);
        return d + ' de ' + MESES[m - 1] + ' de ' + a;
    }

    /** Suma días a una fecha "aaaa-mm-dd" sin pasar por UTC (evita corrimientos de un día). */
    function sumarDias(iso, dias) {
        const [a, m, d] = iso.split('-').map(Number);
        const f = new Date(a, m - 1, d + dias);
        return f.getFullYear() + '-' + String(f.getMonth() + 1).padStart(2, '0') + '-' + String(f.getDate()).padStart(2, '0');
    }

    function revisarFecha(campo, avisar) {
        let mensaje = '';
        const v = campo.value;
        if (v) {
            if (campo.min && v < campo.min) mensaje = 'Elija una fecha desde el ' + legible(campo.min) + '.';
            else if (campo.max && v > campo.max) mensaje = 'Elija una fecha hasta el ' + legible(campo.max) + '.';
        }
        if (mensaje && campo.dataset.mensajeFecha) mensaje = campo.dataset.mensajeFecha + ' ' + mensaje;
        campo.setCustomValidity(mensaje);
        if (mensaje && avisar) campo.reportValidity();
        return !mensaje;
    }

    function enlazarFechas(fin) {
        const inicio = document.getElementById(fin.dataset.despuesDe);
        if (!inicio) return;
        const diasMin = Number(fin.dataset.diasMin || 0);
        const diasMax = fin.dataset.diasMax ? Number(fin.dataset.diasMax) : null;
        const maxPropio = fin.max;               // tope fijo que ya traía el campo (si lo tenía)
        const minPropio = fin.min;
        const actualizar = () => {
            if (inicio.value) {
                fin.min = sumarDias(inicio.value, diasMin);
                let max = diasMax !== null ? sumarDias(inicio.value, diasMax) : '';
                if (maxPropio && (!max || maxPropio < max)) max = maxPropio;
                if (max) fin.max = max; else fin.removeAttribute('max');
            } else {
                if (minPropio) fin.min = minPropio; else fin.removeAttribute('min');
                if (maxPropio) fin.max = maxPropio; else fin.removeAttribute('max');
            }
            if (fin.value) revisarFecha(fin, false);
        };
        inicio.addEventListener('change', actualizar);
        inicio.addEventListener('input', actualizar);
        actualizar();
    }


    /** Reemplaza el valor del campo sin mover el cursor al final cuando no hace falta. */
    function aplicar(campo, nuevo) {
        if (campo.value === nuevo) return;
        const desdeElFinal = campo.value.length - (campo.selectionStart ?? campo.value.length);
        campo.value = nuevo;
        const pos = Math.max(0, nuevo.length - desdeElFinal);
        try { campo.setSelectionRange(pos, pos); } catch (e) { /* algunos tipos no admiten cursor */ }
    }

    function filtrarDigitos(campo) {
        let digitos = campo.value.replace(/[^0-9]/g, '');
        const grupo = Number(campo.dataset.agrupar || 0);
        if (grupo > 0) digitos = digitos.match(new RegExp('.{1,' + grupo + '}', 'g'))?.join(' ') || '';
        aplicar(campo, digitos);
    }

    function filtrarMesAnio(campo) {
        const d = campo.value.replace(/[^0-9]/g, '').slice(0, 4);
        aplicar(campo, d.length > 2 ? d.slice(0, 2) + '/' + d.slice(2) : d);
    }

    function filtrarLetras(campo) {
        aplicar(campo, campo.value.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ '\-]/g, '').replace(/ {2,}/g, ' '));
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('input[inputmode="numeric"]:not([data-telefono-peru]):not([data-formato])').forEach(campo => {
            campo.addEventListener('input', () => filtrarDigitos(campo));
        });
        document.querySelectorAll('input[data-formato="mmaa"]').forEach(campo => {
            campo.addEventListener('input', () => filtrarMesAnio(campo));
        });
        document.querySelectorAll('input[data-solo="letras"], input[name="nombres"], input[name="apellidos"], input[name="titular"]').forEach(campo => {
            campo.addEventListener('input', () => filtrarLetras(campo));
        });
        document.querySelectorAll('input[data-despues-de]').forEach(enlazarFechas);
        document.querySelectorAll('input[type="date"]').forEach(campo => {
            campo.addEventListener('change', () => revisarFecha(campo, true));
            campo.addEventListener('input', () => revisarFecha(campo, false));
            if (campo.value) revisarFecha(campo, false);
        });
        document.querySelectorAll('input[type="number"]').forEach(campo => {
            campo.addEventListener('change', () => { if (!campo.checkValidity()) campo.reportValidity(); });
        });
        // Al enviar, se revisan otra vez todas las fechas (por si el navegador no aplica min y max).
        document.querySelectorAll('form').forEach(form => {
            form.addEventListener('submit', evento => {
                let ok = true;
                form.querySelectorAll('input[type="date"]').forEach(c => { if (!revisarFecha(c, ok)) ok = false; });
                if (!ok) { evento.preventDefault(); evento.stopImmediatePropagation(); }
            }, true);
        });
    });

    // Para otras pantallas que calculan montos (anticipo mínimo, total).
    window.AutoRentFechas = { sumarDias, revisarFecha };
})();
