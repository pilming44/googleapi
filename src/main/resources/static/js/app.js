// 카드 상태 변경: 버튼 클릭 → 서버에 상태 저장 → 성공 시 카드를 화면에서 제거하고 탭 뱃지 갱신.
// (요구사항 8: 상태 버튼을 누르면 해당 영상이 즉시 해당 탭으로 이동)
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.status-btn').forEach((btn) => {
        btn.addEventListener('click', () => changeStatus(btn));
    });
});

async function changeStatus(btn) {
    const videoId = btn.getAttribute('data-video-id');
    const status = btn.getAttribute('data-status');
    const card = btn.closest('.card');

    // 같은 카드의 버튼들 중복 클릭 방지
    if (card) {
        card.querySelectorAll('.status-btn').forEach((b) => (b.disabled = true));
    }

    try {
        const res = await fetch('/api/videos/' + encodeURIComponent(videoId) + '/status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: status }),
        });
        if (!res.ok) {
            throw new Error('상태 변경 실패 (HTTP ' + res.status + ')');
        }
        const data = await res.json();
        updateBadges(data.counts);
        removeCard(card);
    } catch (e) {
        alert(e.message);
        if (card) {
            card.querySelectorAll('.status-btn').forEach((b) => (b.disabled = false));
        }
    }
}

function removeCard(card) {
    if (!card) {
        return;
    }
    card.classList.add('removing');
    setTimeout(() => {
        card.remove();
        const grid = document.querySelector('.grid');
        if (grid && grid.querySelectorAll('.card').length === 0) {
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = '이 탭에 영상이 없습니다.';
            grid.replaceWith(empty);
        }
    }, 150);
}

function updateBadges(counts) {
    if (!counts) {
        return;
    }
    Object.keys(counts).forEach((status) => {
        const badge = document.querySelector('.tab-badge[data-status="' + status + '"]');
        if (badge) {
            badge.textContent = counts[status];
        }
    });
}
