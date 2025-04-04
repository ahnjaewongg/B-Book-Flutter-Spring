import { showAlert } from './utils.js';
import { loadBooks } from './bookList.js';
import { loadCategories } from './bookCategory.js';

// 도서 이미지 첨부
export function initFileInput(inputId, previewId) {
  const $input = $(`#${inputId}`);
  const $preview = $(`#${previewId}`);
  const $label = $input.prev();

  $input.on('change', function(e) {
    // 선택한 첫 번째 파일
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = function(e) {
        // 읽은 데이터를 미리보기 이미지의 src에 넣음
        $preview.attr('src', e.target.result).show();
      }
      // 선택한 파일을 Data URL로 읽음
      reader.readAsDataURL(file);
      $label.html(`<i class="fas fa-check"></i> ${file.name}`);
    }
  });

  // 이미지 드래그 앤 드롭 기능
  $label.on({
    'dragover': function(e) {
      e.preventDefault();
      $(this).addClass('dragover');
    },
    'dragleave': function(e) {
      e.preventDefault();
      $(this).removeClass('dragover');
    },
    'drop': function(e) {
      e.preventDefault();
      $(this).removeClass('dragover');
      $input[0].files = e.originalEvent.dataTransfer.files;
      $input.trigger('change');
    },
  });
}

// 폼 생성
export function createBookFormDto(formId) {
  const prefix = formId === '#editBookForm' ? 'edit' : 'add';
  return {
    title: $(`#${prefix}Title`).val().trim(),
    author: $(`#${prefix}Author`).val().trim(),
    publisher: $(`#${prefix}Publisher`).val().trim(),
    price: parseInt($(`#${prefix}Price`).val()),
    stock: parseInt($(`#${prefix}Stock`).val()),
    mainCategory: $(`#${prefix}MainCategory`).val(),
    midCategory: $(`#${prefix}MidCategory`).val() || '',
    subCategory: $(`#${prefix}SubCategory`).val() || '',
    detailCategory: $(`#${prefix}DetailCategory`).val() || '',
    description: $(`#${prefix}Description`).val().trim()
  };
}

// 도서 저장
export function saveBook() {
  const isEdit = $(this).attr('id') === 'updateBookBtn';
  const modalId = isEdit ? '#editBookModal' : '#addBookModal';
  const formId = isEdit ? '#editBookForm' : '#addBookForm';
  const prefix = formId === '#editBookForm' ? 'edit' : 'add';

  if (!validateBookForm(formId)) {
      return;
  }

  const formData = new FormData();
  const bookFormDto = createBookFormDto(formId);
  const bookId = isEdit ? $('#editBookId').val() : null;

  formData.append('bookFormDto', new Blob([JSON.stringify(bookFormDto)], {
    type: 'application/json'
  }));

  const bookImage = $(`${formId} #${prefix}BookImage`)[0].files[0];
  if (bookImage) {
    formData.append('bookImage', bookImage);
  }

  $.ajax({
    url: isEdit ? `/admin/items/${bookId}` : '/admin/items/new',
    type: isEdit ? 'PUT' : 'POST',
    data: formData,
    processData: false,
    contentType: false,
    success: function(response) {
      $(modalId).modal('hide');
      showAlert(isEdit ? '수정 완료' : '저장 완료', 'success',
                          `도서가 성공적으로 ${isEdit ? '수정' : '추가'}되었습니다.`
      ).then((result) => {
        if (result.isConfirmed) {
            loadBooks();
        }
      });
      resetForm(formId);
    },
    error: function() {
      showAlert(isEdit ? '수정 실패' : '저장 실패', 'error',
                         `도서 ${isEdit ? '수정' : '추가'} 중 오류가 발생했습니다.`);
    }
  });
}

// 유효성 검사
function validateBookForm(formId) {
    const prefix = formId === '#editBookForm' ? 'edit' : 'add';
    const requiredFields = [
        { id: `${prefix}Title`, name: '제목' },
        { id: `${prefix}Author`, name: '저자' },
        { id: `${prefix}Publisher`, name: '출판사' },
        { id: `${prefix}Stock`, name: '재고' },
        { id: `${prefix}MainCategory`, name: '대분류' },
        { id: `${prefix}MidCategory`, name: '중분류' },
        { id: `${prefix}SubCategory`, name: '소분류' }
    ];

    // 필수 입력
    for (const field of requiredFields) {
        if (!validateField(field)) {
            return false;
        }
    }

    // 양수 검사
    if (!validateNumericField('Price', '가격', formId)) {
        return false;
    }

    // 수정 시 이미지 필수 아님
    const isEdit = formId === '#editBookForm';
    if (!isEdit && !validateImageFile(formId)) {
        return false;
    }

    return true;
}

// 필수 입력
function validateField(field) {
    const value = $(`#${field.id}`).val();
    if (!value || value.trim() === '') {
        showAlert('입력 오류', 'warning', `${field.name}을(를) 입력해주세요.`);
        $(`#${field.id}`).focus();
        return false;
    }
    return true;
}

// 숫자 입력
function validateNumericField(fieldId, fieldName, formId = '#addBookForm') {
    const prefix = formId === '#editBookForm' ? 'edit' : 'add';
    const value = $(`#${prefix}${fieldId}`).val();

    // 숫자가 아닌 문자 제거
    const numericValue = String(value).replace(/[^\d]/g, '');

    if (!numericValue || parseInt(numericValue) < 0) {
        showAlert('입력 오류', 'warning', `${fieldName}은(는) 0 이상의 숫자로 입력해주세요.`);
        $(`#${prefix}${fieldId}`).focus();
        return false;
    }

    $(`#${prefix}${fieldId}`).val(numericValue);
    return true;
}

// 이미지 검사
function validateImageFile(formId) {
    const prefix = formId === '#editBookForm' ? 'edit' : 'add';
    const imageFile = $(`${formId} #${prefix}BookImage`)[0].files[0];
    const isEdit = formId === '#editBookForm';

    // 수정이 아닐 경우 검사
    if (!isEdit && !imageFile) {
        showAlert('입력 오류', 'warning', '도서 이미지를 선택해주세요.');
        return false;
    }
    return true;
}

// 이미지 미리보기
export function handleImagePreview() {
    const formId = $(this).closest('form').attr('id');
    const prefix = formId === 'editBookForm' ? 'edit' : 'add';
    const file = this.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function(e) {
            $(`#${prefix}ImagePreview`)
            .html(`<img src="${e.target.result}" class="img-fluid" alt="미리보기">`);
        };
        reader.readAsDataURL(file);
    }
}

// 숫자 검사
export function handleNumericInput() {
    const value = $(this).val();
    if (value) {
        const numericValue = value.replace(/[^\d]/g, '');
        $(this).val(numericValue);
    }
}

// 폼 초기화 함수
export function resetForm(formId = '#addBookForm') {
    const prefix = formId === '#editBookForm' ? 'edit' : 'add';
    $(formId)[0].reset();
    $(`#${prefix}ImagePreview`).empty();
    $(`#${prefix}MainCategory`).val('');
    $(`#${prefix}MidCategory`).val('');
    $(`#${prefix}SubCategory`).val('');
    $(`#${prefix}DetailCategory`).val('');

    if (formId === '#editBookForm') {
        $('#editBookId').val('');
    }

    loadCategories(prefix);
}