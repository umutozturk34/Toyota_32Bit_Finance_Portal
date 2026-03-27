<#import "template.ftl" as layout>
<@layout.emailLayout>
    <p>Merhaba <strong>${user.firstName!""}</strong>,</p>
    <p>Hesabınız için gerçekleştirmeniz gereken bir işlem bulunmaktadır.</p>
    <p>Lütfen aşağıdaki butona tıklayarak işleminizi tamamlayın.</p>
    <p>
        <a href="${link}" class="email-cta">İşlemi Tamamla</a>
    </p>
    <div class="email-info-box">
        <p>Bu bağlantı <strong>${linkExpiration}</strong> dakika içinde geçerliliğini yitirecektir.</p>
    </div>
</@layout.emailLayout>
