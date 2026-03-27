<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabınızda şifre değişikliği tespit edildi.</p>
    <div class="email-info-box">
        <p>Bu değişikliği siz yapmadıysanız lütfen aşağıdaki bağlantıdan şifrenizi hemen sıfırlayın.</p>
    </div>
    <p>
        <a href="${link}" class="email-cta">Şifremi Sıfırla</a>
    </p>
</@layout.emailLayout>
