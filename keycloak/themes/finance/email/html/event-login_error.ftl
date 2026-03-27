<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabınıza yeni bir cihazdan veya konumdan oturum açıldığını tespit ettik.</p>
    <div class="email-info-box">
        <p><strong>IP Adresi:</strong> ${event.ipAddress!""}</p>
    </div>
    <p>Bu oturum açma işlemini siz gerçekleştirmediyseniz lütfen derhal şifrenizi değiştirin.</p>
    <p>
        <a href="${link}" class="email-cta">Şifremi Değiştir</a>
    </p>
</@layout.emailLayout>
