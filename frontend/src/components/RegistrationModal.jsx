import React, { useState } from 'react';
import '../css/RegistrationModal.css';

export default function RegistrationModal({ questions, onSubmit, onClose }) {
  const [values, setValues] = useState({});
  const [err, setErr] = useState('');
  const set = (id, v) => setValues(prev => ({ ...prev, [id]: v }));

  const submit = (e) => {
    e.preventDefault();
    for (const q of questions) {
      const v = (values[q.id] || '').trim();
      if (q.required && !v) { setErr(`Ответьте на вопрос: ${q.label}`); return; }
    }
    onSubmit(values);
  };

  return (
    <div className="rmodal__overlay" onMouseDown={onClose}>
      <div className="rmodal" onMouseDown={e => e.stopPropagation()}>
        <h3 className="rmodal__title">Анкета участника</h3>
        <form onSubmit={submit} className="rmodal__form">
          {questions.map(q => (
            <div className="rmodal__field" key={q.id}>
              <label className="rmodal__label">{q.label}{q.required && <span className="rmodal__req"> *</span>}</label>
              {q.type === 'SINGLE' ? (
                <div className="rmodal__opts">
                  {(q.options || []).map(o => (
                    <label key={o} className="rmodal__opt">
                      <input type="radio" name={q.id} value={o}
                             checked={values[q.id] === o} onChange={() => set(q.id, o)} />
                      {o}
                    </label>
                  ))}
                </div>
              ) : (
                <textarea className="rmodal__input" maxLength={1000}
                          value={values[q.id] || ''} onChange={e => set(q.id, e.target.value)} />
              )}
            </div>
          ))}
          {err && <div className="rmodal__err">{err}</div>}
          <div className="rmodal__actions">
            <button type="button" className="rmodal__cancel" onClick={onClose}>Отмена</button>
            <button type="submit" className="rmodal__submit">Записаться</button>
          </div>
        </form>
      </div>
    </div>
  );
}
