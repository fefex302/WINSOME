

public class Comment {
	String idAuthor;
	String comment;
	
	public Comment(String idAuthor,String comment) {
		this.idAuthor = idAuthor;
		this.comment = comment;
	}
	
	public String getAuthor() {
		return idAuthor;
	}
	
	public String getComment() {
		return comment;
	}
}
