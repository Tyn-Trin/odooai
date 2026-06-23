const models = {
    'claude': [
        { value: 'claude-haiku-4-5-20251001', label: 'Claude Haiku' }
    ]
};

function updateModels() {
    const provider = document.getElementById('providerSelect').value;
    const modelSelect = document.getElementById('modelSelect');

    modelSelect.innerHTML = '<option value="">-- เลือก Model --</option>';
    (models[provider] || []).forEach(m => {
        modelSelect.innerHTML += `<option value="${m.value}">${m.label}</option>`;
    });
}
