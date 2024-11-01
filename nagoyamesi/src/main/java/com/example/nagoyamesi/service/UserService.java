package com.example.nagoyamesi.service;

import java.util.Calendar;
import java.util.Date;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nagoyamesi.entity.PasswordResetToken;
import com.example.nagoyamesi.entity.Role;
import com.example.nagoyamesi.entity.Subscription;
import com.example.nagoyamesi.entity.User;
import com.example.nagoyamesi.form.PasswordResetForm;
import com.example.nagoyamesi.form.SignupForm;
import com.example.nagoyamesi.form.UserEditForm;
import com.example.nagoyamesi.repository.PasswordResetTokenRepository;
import com.example.nagoyamesi.repository.RoleRepository;
import com.example.nagoyamesi.repository.SubscriptionRepository;
import com.example.nagoyamesi.repository.UserRepository;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserDetailsService userDetailsService;
	private final SubscriptionRepository subscriptionRepository;
	
	public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
			PasswordResetTokenRepository passwordResetTokenRepository, UserDetailsService userDetailsService,
			SubscriptionRepository subscriptionRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userDetailsService = userDetailsService;
		this.subscriptionRepository = subscriptionRepository;
	}

	@Transactional
	public User create(SignupForm signupForm) {
		User user = new User();
		Role role = roleRepository.findByName("ROLE_GENERAL");

		user.setName(signupForm.getName());
		user.setEmail(signupForm.getEmail());
		user.setPassword(passwordEncoder.encode(signupForm.getPassword()));
		user.setRole(role);
		user.setEnabled(false);

		return userRepository.save(user);
	}

	@Transactional
	public void update(UserEditForm userEditForm) {
		User user = userRepository.getReferenceById(userEditForm.getId());

		user.setName(userEditForm.getName());
		user.setEmail(userEditForm.getEmail());

		userRepository.save(user);
	}

	//パスワードリセット機能
	@Transactional
	public void passwordUpdate(PasswordResetForm passwordResetForm) {
		User user = userRepository.findByEmail(passwordResetForm.getEmail());

		user.setEnabled(true);

		userRepository.save(user);
	}

	@Transactional
	public void enableUser(User user) {
		user.setEnabled(true);
		userRepository.save(user);
	}
	

	@Transactional
	public void upgradeRole(Integer userId) {
		User userUpdate = userRepository.getReferenceById(userId);
		
		Role role = roleRepository.findByName("ROLE_PREMIUM");
		
		userUpdate.setRole(role);

		userRepository.save(userUpdate);
	}

	@Transactional
	public void downgradeRole(Integer userId) {

		User user = userRepository.getReferenceById(userId);
		Role role = roleRepository.findByName("ROLE_GENERAL");

		user.setRole(role);
		user.setEnabled(true);

		userRepository.save(user);
		

        reauthenticateUser(user.getEmail());

	}

	//メールアドレスが登録してあるか
	public boolean isEmailRegistered(String email) {
		User user = userRepository.findByEmail(email);
		return user != null;
	}

	//パスワードが確認用と一致しているか
	public boolean isSamePassword(String password, String passwordConfirmation) {
		return password.equals(passwordConfirmation);
	}

	//メールアドレスが変更されてるかチェック
	public boolean isEmailChanged(UserEditForm userEditForm) {
		User currentUser = userRepository.getReferenceById(userEditForm.getId());
		return !userEditForm.getEmail().equals(currentUser.getEmail());
	}

	public void createPasswordResetTokenForUser(String email, String token) {
		User user = userRepository.findByEmail(email);
		if (user == null) {
			throw new RuntimeException("ユーザーが見つかりません。");
		}
		PasswordResetToken myToken = new PasswordResetToken();
		myToken.setToken(token);
		myToken.setUser(user);
		myToken.setExpiryDate(calcExpiryDate(24 * 60));
		passwordResetTokenRepository.save(myToken);
	}

	private Date calcExpiryDate(int i) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.MINUTE, i);
		return new Date(cal.getTime().getTime());
	}

	public PasswordResetToken getPasswordResetToken(String token) {
		return passwordResetTokenRepository.findByToken(token);
	}

	public boolean updatePassword(String token, String newPassword) {
		PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token);

		if (resetToken == null) {
			throw new IllegalArgumentException("Invalid token");
		}
		Calendar cal = Calendar.getInstance();
		if ((resetToken.getExpiryDate().getTime() - cal.getTime().getTime()) <= 0) {
			return false;
		}

		User user = resetToken.getUser();
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		passwordResetTokenRepository.delete(resetToken);
		return true;
	}
	
	private void reauthenticateUser(String email) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
	
	public Subscription findSubscriptionByStripeCustomerId(String stripeCustomerId) {
		
		return subscriptionRepository.findByStripeCustomerId(stripeCustomerId);
		}
	
}