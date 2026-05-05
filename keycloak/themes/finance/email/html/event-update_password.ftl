<#import "template.ftl" as layout>
<@layout.emailLayout title="Şifre değişikliği" subtitle="Hesabında şifre güncellendi">
    <p>Merhaba <strong>${user.firstName!''}</strong>,</p>
    <p>Hesabında şifre değişikliği tespit edildi.</p>
    <div class="email-info-box">
        <p>Bu değişikliği sen yapmadıysan aşağıdaki bağlantıdan şifreni hemen sıfırla.</p>
    </div>
    <div class="cta-wrap">
        <a href="${link}" class="email-cta">Şifremi Sıfırla</a>
    </div>
</@layout.emailLayout>
