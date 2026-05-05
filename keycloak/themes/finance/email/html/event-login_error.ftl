<#import "template.ftl" as layout>
<@layout.emailLayout title="Şüpheli oturum açma" subtitle="Hesabında yeni bir giriş tespit edildi">
    <p>Merhaba <strong>${user.firstName!''}</strong>,</p>
    <p>Hesabına yeni bir cihazdan veya konumdan oturum açıldı.</p>
    <div class="email-info-box">
        <p><strong>IP Adresi:</strong> <code>${event.ipAddress!''}</code></p>
    </div>
    <p>Bu girişi sen yapmadıysan şifreni hemen değiştirmen önerilir.</p>
    <div class="cta-wrap">
        <a href="${link}" class="email-cta">Şifremi Değiştir</a>
    </div>
</@layout.emailLayout>
