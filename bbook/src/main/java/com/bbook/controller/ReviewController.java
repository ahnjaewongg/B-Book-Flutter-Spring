package com.bbook.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.bbook.constant.ActivityType;
import com.bbook.constant.ReportType;
import com.bbook.dto.ReviewDto;
import com.bbook.dto.ReviewRequestDto;
import com.bbook.dto.ReviewStatsDto;
import com.bbook.dto.ReviewUpdateDto;
import com.bbook.service.MemberActivityService;
import com.bbook.service.MemberService;
import com.bbook.service.ReviewService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/reviews")
public class ReviewController {
	private final ReviewService reviewService;
	private final MemberService memberService;
	private final MemberActivityService memberActivityService;

	// 리뷰 생성
	@PostMapping
	@ResponseBody
	public ResponseEntity<Map<String, Object>> createReview(
			@ModelAttribute ReviewRequestDto request,
			@AuthenticationPrincipal UserDetails userDetails) {
		try {
			String email = userDetails.getUsername();
			Long memberId = memberService.getMemberIdByEmail(email);

			ReviewDto reviewDto = ReviewDto.builder()
							.memberId(memberId)
							.bookId(request.getBookId())
							.rating(request.getRating())
							.content(request.getContent())
							.reviewImages(request.getReviewImages())
							.tagType(request.getTagType())
							.build();

			// 리뷰 DB에 저장(클린봇 검사 포함)
			boolean isBlocked = reviewService.createReview(reviewDto);
			Map<String, Object> response = new HashMap<>();

			// 리뷰 등록 시 클린봇 검사 결과에 따른 알림창 설정
			if (isBlocked) {
				response.put("success", true);
				response.put("blocked", true);
				response.put("message", "악플성 댓글이 감지되었습니다.");
				return ResponseEntity.ok(response);
			}

			// 리뷰 활동 기록 저장
			if (email != null) {
				memberActivityService
						.saveActivity(email, request.getBookId(), ActivityType.REVIEW);
			}

			// 통계 데이터 업데이트
			ReviewStatsDto updatedStats = reviewService.getReviewStats(request.getBookId());
			Double updatedAvgRating = reviewService.getAverageRatingByBookId(request.getBookId());

			response.put("success", true);
			response.put("blocked", false);
			response.put("message", "리뷰가 등록되었습니다.");
			response.put("stats", updatedStats);
			response.put("avgRating", updatedAvgRating);

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false,
					"error", e.getMessage()));
		}
	}

	// 리뷰 조회
	@GetMapping("/{bookId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getReviews(
			@PathVariable("bookId") Long bookId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "likes") String sort,
			@AuthenticationPrincipal UserDetails userDetails) {
		String email = userDetails != null ? userDetails.getUsername() : null;
		Long currentMemberId = email != null ?
				memberService.getMemberIdByEmail(email) : null;

		PageRequest pageRequest = PageRequest.of(
				page, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<ReviewDto> reviews =
				reviewService.getBookReviews(bookId, currentMemberId, pageRequest, sort);

		ReviewStatsDto stats = reviewService.getReviewStats(bookId);
		Double avgRating = reviewService.getAverageRatingByBookId(bookId);

		Map<String, Object> response = new HashMap<>();
		response.put("content", reviews.getContent());
		response.put("totalPages", reviews.getTotalPages());
		response.put("totalElements", reviews.getTotalElements());
		response.put("size", reviews.getSize());
		response.put("number", reviews.getNumber());
		response.put("first", reviews.isFirst());
		response.put("last", reviews.isLast());
		response.put("empty", reviews.isEmpty());
		response.put("stats", stats);
		response.put("avgRating", avgRating);

		return ResponseEntity.ok(response);
	}

	// 평균 평점 조회
	@GetMapping("/average/{bookId}")
	@ResponseBody
	public ResponseEntity<Double> getAverageRating(@PathVariable("bookId") Long bookId) {
		try {
			Double avgRating = reviewService.getAverageRatingByBookId(bookId);
			return ResponseEntity.ok(avgRating);
		} catch (Exception e) {
			System.out.println("오류 발생 : " + e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	// 리뷰 수정
	@PatchMapping("/{reviewId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> updateReview(
			@PathVariable("reviewId") Long reviewId,
			@ModelAttribute ReviewUpdateDto updateDto,
			@AuthenticationPrincipal UserDetails userDetails) {
		try {
			String email = userDetails.getUsername();
			Long memberId = memberService.getMemberIdByEmail(email);

			reviewService.updateReview(reviewId, memberId, updateDto);

			Long bookId = reviewService.getBookIdByReviewId(reviewId);
			ReviewStatsDto updatedStats = reviewService.getReviewStats(bookId);
			Double updatedAvgRating = reviewService.getAverageRatingByBookId(bookId);

			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("stats", updatedStats);
			response.put("avgRating", updatedAvgRating);

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false, "error", e.getMessage()));
		}
	}

	@DeleteMapping("/{reviewId}")
	public ResponseEntity<Void> deleteReview(@PathVariable("reviewId") Long reviewId) {
		reviewService.deleteReview(reviewId);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/count/{bookId}")
	@ResponseBody
	public ResponseEntity<Long> getReviewCount(@PathVariable("bookId") Long bookId) {
		long count = reviewService.getReviewCount(bookId);
		return ResponseEntity.ok(count);
	}

	// 리뷰 좋아요
	@PostMapping("/{reviewId}/like")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable Long reviewId,
			@AuthenticationPrincipal UserDetails userDetails) {
		if (userDetails == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String email = userDetails.getUsername();
		Long memberId = memberService.getMemberIdByEmail(email);

		try {
			Map<String, Object> result = reviewService.toggleLike(reviewId, memberId);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	// 리뷰 통계 조회
	@GetMapping("/stats/{bookId}")
	@ResponseBody
	public ResponseEntity<ReviewStatsDto> getReviewStats(
			@PathVariable("bookId") Long bookId) {
		try {
			ReviewStatsDto stats = reviewService.getReviewStats(bookId);
			return ResponseEntity.ok(stats);
		} catch (Exception e) {
			System.out.println("오류 발생 : " + e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}

	@GetMapping("/report")
	public ResponseEntity<Void> checkLoginForReport(
			@AuthenticationPrincipal UserDetails userDetails) {
		if (userDetails == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok().build();
	}

	// 리뷰 신고
	@PostMapping("/report")
	public ResponseEntity<Void> reportReview(
			@RequestParam Long reviewId, @RequestParam ReportType reportType,
			@RequestParam String content,
			@AuthenticationPrincipal UserDetails userDetails) {
		if (userDetails == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String email = userDetails.getUsername();
		Long memberId = memberService.getMemberIdByEmail(email);

		try {
			reviewService.reportReview(reviewId, memberId, reportType, content);
			return ResponseEntity.ok().build();
		} catch (IllegalStateException e) {
			System.out.println(e.getMessage());
			return ResponseEntity.badRequest().build();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}
}
