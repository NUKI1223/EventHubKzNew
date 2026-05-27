import github_icon from "../assets/images/github_icon.png";
import telegram_icon from "../assets/images/telegram_icon.png";
import facebook_icon from "../assets/images/facebook_icon.png";
import instagram_icon from "../assets/images/instagram_icon.png";

export const SOCIALS = [
  { field: 'telegram',  contactKey: 'additionalProp1', prefix: 'https://t.me/',         label: 'Telegram',  icon: telegram_icon  },
  { field: 'github',    contactKey: 'additionalProp2', prefix: 'https://github.com/',   label: 'GitHub',    icon: github_icon    },
  { field: 'instagram', contactKey: 'additionalProp3', prefix: 'https://instagram.com/', label: 'Instagram', icon: instagram_icon },
  { field: 'facebook',  contactKey: 'additionalProp4', prefix: 'https://facebook.com/', label: 'Facebook',  icon: facebook_icon  },
];

export function contactsFromForm(formData) {
  return SOCIALS.reduce((acc, s) => {
    acc[s.contactKey] = formData[s.field] ? `${s.prefix}${formData[s.field]}` : '';
    return acc;
  }, {});
}

export function formFromContacts(contacts) {
  return SOCIALS.reduce((acc, s) => {
    acc[s.field] = contacts?.[s.contactKey]?.replace(s.prefix, '') || '';
    return acc;
  }, {});
}
