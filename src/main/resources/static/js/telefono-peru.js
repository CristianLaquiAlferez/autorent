/* Valida la longitud y los digitos de los telefonos peruanos en el navegador. */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('input[data-telefono-peru]').forEach(campo => {
        const limitar = () => {
            let digitos = campo.value.replace(/[^0-9]/g, '');
            if (digitos.length > 9 && digitos.startsWith('51') && digitos.length <= 11) {
                digitos = digitos.slice(2);
            }
            digitos = digitos.slice(0, 9);
            if (campo.value !== digitos) campo.value = digitos;
        };
        campo.addEventListener('input', limitar);
        campo.addEventListener('paste', evento => {
            const texto = evento.clipboardData?.getData('text');
            if (!texto) return;
            const digitos = texto.replace(/[^0-9]/g, '');
            if (digitos.length > 9) {
                evento.preventDefault();
                campo.value = digitos.startsWith('51') && digitos.length <= 11
                    ? digitos.slice(2, 11) : digitos.slice(0, 9);
                limitar();
            }
        });
        limitar();
    });
});
