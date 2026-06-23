import React from 'react';
import { useTranslation } from 'react-i18next';
import '../css/QuestionEditor.css';

const MAX_Q = 10;
const MAX_OPT = 10;
let seq = 0;
const newId = () => `q${Date.now().toString(36)}${(seq++).toString(36)}`;

export default function QuestionEditor({ questions, onChange }) {
  const { t } = useTranslation();
  const update = (i, patch) => onChange(questions.map((q, idx) => idx === i ? { ...q, ...patch } : q));
  const add = () => {
    if (questions.length >= MAX_Q) return;
    onChange([...questions, { id: newId(), label: '', type: 'TEXT', required: false, options: [] }]);
  };
  const remove = (i) => onChange(questions.filter((_, idx) => idx !== i));
  const setOpt = (i, j, val) => update(i, { options: questions[i].options.map((o, k) => k === j ? val : o) });
  const addOpt = (i) => questions[i].options.length < MAX_OPT && update(i, { options: [...questions[i].options, ''] });
  const removeOpt = (i, j) => update(i, { options: questions[i].options.filter((_, k) => k !== j) });

  return (
    <div className="qed">
      {questions.map((q, i) => (
        <div className="qed__item" key={q.id}>
          <div className="qed__row">
            <input className="qed__label" placeholder={t('requestForm.qPlaceholder')} maxLength={200}
                   value={q.label} onChange={e => update(i, { label: e.target.value })} />
            <select className="qed__type" value={q.type}
                    onChange={e => update(i, { type: e.target.value, options: e.target.value === 'SINGLE' ? (q.options.length ? q.options : ['']) : [] })}>
              <option value="TEXT">{t('requestForm.qTypeText')}</option>
              <option value="SINGLE">{t('requestForm.qTypeSingle')}</option>
            </select>
            <label className="qed__req">
              <input type="checkbox" checked={q.required} onChange={e => update(i, { required: e.target.checked })} />
              {t('requestForm.qRequired')}
            </label>
            <button type="button" className="qed__del" onClick={() => remove(i)} aria-label={t('requestForm.qDeleteLabel')}>×</button>
          </div>
          {q.type === 'SINGLE' && (
            <div className="qed__opts">
              {q.options.map((o, j) => (
                <div className="qed__opt" key={j}>
                  <input placeholder={t('requestForm.qOptionPlaceholder', { n: j + 1 })} value={o} maxLength={200}
                         onChange={e => setOpt(i, j, e.target.value)} />
                  <button type="button" onClick={() => removeOpt(i, j)} aria-label={t('requestForm.qDeleteOptionLabel')}>×</button>
                </div>
              ))}
              {q.options.length < MAX_OPT && (
                <button type="button" className="qed__add-opt" onClick={() => addOpt(i)}>{t('requestForm.qAddOption')}</button>
              )}
            </div>
          )}
        </div>
      ))}
      {questions.length < MAX_Q && (
        <button type="button" className="qed__add" onClick={add}>{t('requestForm.qAddQuestion')}</button>
      )}
    </div>
  );
}
