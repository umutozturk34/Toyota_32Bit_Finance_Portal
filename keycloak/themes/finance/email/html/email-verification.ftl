<#import "template.ftl" as layout>
<@layout.emailLayout title="E-posta doğrulaması" subtitle="${user.email!''}">
    <p>Merhaba <strong>${user.firstName!''}</strong>,</p>
    <p>Hesabını doğrulamak için aşağıdaki 6 haneli kodu kullan.</p>
    <div class="code-wrap">
        <div class="email-code-box"><span class="email-code">${code}</span></div>
    </div>
    <div class="email-info-box">
        <p><strong>Geçerlilik:</strong> Bu kod ${linkExpiration} dakika içinde sona erer.</p>
    </div>
    <p>Eğer bu işlemi sen başlatmadıysan e-postayı yok sayabilirsin.</p>
</@layout.emailLayout>
